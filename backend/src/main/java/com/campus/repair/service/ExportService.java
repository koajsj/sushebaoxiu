package com.campus.repair.service;

import com.campus.repair.common.BusinessException;
import com.campus.repair.common.ErrorCode;
import com.campus.repair.mapper.ExportMapper;
import com.campus.repair.vo.UserVO;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.apache.poi.ss.SpreadsheetVersion;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@lombok.RequiredArgsConstructor
@Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
public class ExportService {
    private final ExportMapper mapper;
    private final StatisticsService statistics;
    private final OrderAccessService access;
    private final Clock clock;

    public record ReportFile(String filename,byte[] bytes) {}

    public ReportFile orders(UserVO user) {
        access.requireAdmin(user);
        long maxId=mapper.maxOrderId();
        return report("工单统计",new String[]{"工单编号","报修人","故障类型","报修地点","优先级","当前状态","维修人员","创建时间","完成时间","维修耗时（小时）"},book->{
            book.format(7,"yyyy-mm-dd hh:mm:ss");book.format(8,"yyyy-mm-dd hh:mm:ss");book.format(9,"0.0");
            long after=0;
            while(after<maxId) {
                var rows=mapper.orders(after,maxId,500);
                if(rows.isEmpty())break;
                for(var row:rows) {
                    var phase=OrderPhase.of(row.status(),row.workerId(),row.acceptedTime());
                    book.row(String.valueOf(row.id()),row.studentName(),row.typeName(),row.location(),
                            Map.of("LOW","不紧急","NORMAL","普通","HIGH","较紧急").getOrDefault(row.priority(),row.priority()),
                            phaseLabel(phase),row.workerName(),row.createTime(),row.completeTime(),hours(row.repairSeconds()));
                }
                after=rows.get(rows.size()-1).id();
            }
        },"全量工单；当前状态使用系统统一业务阶段。",
          "完成时间为学生最终确认事件时间，仅已完成/已评价工单展示；旧数据缺失事件时留空。",
          "维修耗时为各已完成维修轮次的耗时之和；每轮口径与Dashboard一致，不含审核、派单和验收等待。未完成记录不计入。");
    }

    public ReportFile workers(UserVO user) {
        access.requireAdmin(user);
        var rows=statistics.workers(user);
        var metrics=mapper.workerMetrics().stream().collect(Collectors.toMap(row->row.workerId(),row->row));
        return report("维修效率",new String[]{"维修人员姓名","完成订单数量","平均评分","平均维修时间（小时）","返工次数","当前任务数量"},book->{
            book.format(2,"0.0");book.format(3,"0.0");
            for(var row:rows) {
                var extra=metrics.get(((Number)row.get("workerId")).longValue());
                book.row(row.get("workerName"),row.get("completedCount"),row.get("averageRating"),
                        extra==null?null:hours(extra.averageRepairSeconds()),extra==null?0L:extra.reworkCount(),row.get("activeCount"));
            }
        },"全量维修人员；完成订单数量、平均评分和当前任务数量直接复用Dashboard统计。",
          "完成订单按学生已确认的工单计数；当前任务为WAIT_ASSIGN、ASSIGNED、PROCESSING。",
          "平均维修时间沿用Dashboard的已完成轮次起止口径，按该轮最终维修记录的维修员归属计算。",
          "返工次数为学生验收未通过的真实事件次数，归属事件记录中的维修员；不把后续接手人员视为责任人。");
    }

    public ReportFile types(UserVO user) {
        access.requireAdmin(user);
        var rows=statistics.types(user);
        return report("故障分析",new String[]{"故障类型","数量","占比"},book->{
            book.format(2,"0.0%");
            for(var row:rows) {
                var percentage=(BigDecimal)row.get("percentage");
                book.row(row.get("typeName"),row.get("count"),percentage==null?null:percentage.movePointLeft(2));
            }
        },"全量故障类型及关联工单，包含尚无工单的类型；数量与占比直接复用Dashboard。",
          "占比保留Dashboard的一位小数百分比口径；无工单时占比留空，不伪装为有效统计值。");
    }

    private static BigDecimal hours(Number seconds) {
        return seconds==null?null:BigDecimal.valueOf(seconds.doubleValue()/3600).setScale(1,RoundingMode.HALF_UP);
    }

    private static String phaseLabel(OrderPhase phase) {
        return switch(phase) {
            case CREATED -> "草稿";case WAIT_AUDIT -> "待审核";case WAIT_DISPATCH -> "待派单";
            case WAIT_ACCEPT -> "待接单";case WAIT_START -> "待开工";case PROCESSING -> "维修中";
            case WAIT_CONFIRM -> "待验收";case FINISHED -> "已完成";case COMMENTED -> "已评价";
            case REJECTED -> "审核驳回";case REWORK_PENDING -> "待安排返工";
        };
    }

    private ReportFile report(String title,String[] headers,Consumer<ReportBook> fill,String... notes) {
        var generated=LocalDateTime.ofInstant(clock.instant(),ZoneId.of("Asia/Shanghai"));
        // Only 100 spreadsheet rows stay in memory. POI owns and removes its temporary files on close.
        try(var workbook=new SXSSFWorkbook(100);var bytes=new ByteArrayOutputStream()) {
            workbook.setCompressTempFiles(true);
            var book=new ReportBook(workbook,title,headers);fill.accept(book);book.finish();
            var description=new ReportBook(workbook,"口径说明",new String[]{"项目","说明"});
            description.row("生成时间",generated.withNano(0)+"（Asia/Shanghai）");
            description.row("数据行数",book.rowCount());
            description.row("空值说明","空白表示尚未产生或暂无有效数据；合法的零计数/零耗时保留为0。");
            for(int index=0;index<notes.length;index++)description.row("统计口径"+(index+1),notes[index]);
            description.finish();workbook.write(bytes);
            return new ReportFile(title+"_"+generated.toLocalDate()+".xlsx",bytes.toByteArray());
        } catch(IOException failure) { throw new UncheckedIOException("Cannot generate Excel report",failure); }
    }

    private static final class ReportBook {
        private final SXSSFWorkbook workbook;
        private final org.apache.poi.xssf.streaming.SXSSFSheet sheet;
        private final CellStyle[] styles;
        private int nextRow=1;

        ReportBook(SXSSFWorkbook workbook,String title,String[] headers) {
            this.workbook=workbook;sheet=workbook.createSheet(title);sheet.trackAllColumnsForAutoSizing();
            sheet.createFreezePane(0,1);sheet.setDefaultRowHeightInPoints(22);
            var font=workbook.createFont();font.setBold(true);font.setColor(IndexedColors.WHITE.getIndex());
            var headerStyle=workbook.createCellStyle();headerStyle.setFont(font);
            headerStyle.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            styles=new CellStyle[headers.length];var header=sheet.createRow(0);header.setHeightInPoints(28);
            for(int index=0;index<headers.length;index++) {
                var cell=header.createCell(index);cell.setCellValue(headers[index]);cell.setCellStyle(headerStyle);
                styles[index]=workbook.createCellStyle();styles[index].setVerticalAlignment(VerticalAlignment.CENTER);
            }
        }

        void format(int column,String value) { styles[column].setDataFormat(workbook.createDataFormat().getFormat(value)); }
        int rowCount() { return nextRow-1; }
        void row(Object... values) {
            if(nextRow>SpreadsheetVersion.EXCEL2007.getLastRowIndex())throw new BusinessException(ErrorCode.EXPORT_TOO_LARGE);
            var row=sheet.createRow(nextRow++);
            for(int index=0;index<values.length;index++) {
                var cell=row.createCell(index);cell.setCellStyle(styles[index]);
                var value=values[index];
                if(value instanceof Number number)cell.setCellValue(number.doubleValue());
                else if(value instanceof LocalDateTime time)cell.setCellValue(time);
                else if(value!=null)cell.setCellValue(value.toString()); // Text is never interpreted as a formula.
            }
        }
        void finish() {
            sheet.setAutoFilter(new CellRangeAddress(0,Math.max(0,nextRow-1),0,styles.length-1));
            for(int index=0;index<styles.length;index++) {
                sheet.autoSizeColumn(index);
                sheet.setColumnWidth(index,Math.min(60*256,Math.max(12*256,sheet.getColumnWidth(index)+2*256)));
            }
        }
    }
}

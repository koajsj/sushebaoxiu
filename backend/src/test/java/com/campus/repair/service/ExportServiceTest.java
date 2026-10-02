package com.campus.repair.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.campus.repair.common.BusinessException;
import com.campus.repair.common.ErrorCode;
import com.campus.repair.mapper.*;
import com.campus.repair.security.UserRole;
import com.campus.repair.vo.*;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class ExportServiceTest {
    private final ExportMapper mapper=mock(ExportMapper.class);
    private final OrderAccessService access=new OrderAccessService(mock(StudentMapper.class),mock(WorkerMapper.class),mock(UserMapper.class));
    private final StatisticsMapper statisticsMapper=mock(StatisticsMapper.class);
    private final Clock clock=Clock.fixed(Instant.parse("2026-09-30T16:30:00Z"),ZoneOffset.UTC);
    private final StatisticsService statistics=new StatisticsService(statisticsMapper,access,clock);
    private final ExportService service=new ExportService(mapper,statistics,access,clock);
    private final UserVO admin=new UserVO(3,"admin001","管理员",null,UserRole.ADMIN);

    @Test void reportsReuseDashboardValuesAndKeepMissingMetricsBlank() throws Exception {
        when(statisticsMapper.typeCounts()).thenReturn(List.of(Map.of("typeId",1L,"typeName","电路","amount",3L),Map.of("typeId",2L,"typeName","门窗","amount",1L)));
        var worker=new HashMap<String,Object>();worker.put("workerId",7L);worker.put("workerName","王师傅");
        worker.put("completedCount",2L);worker.put("activeCount",1L);worker.put("averageRating",new BigDecimal("4.5"));
        when(statisticsMapper.workerCounts()).thenReturn(List.of(worker));
        when(mapper.workerMetrics()).thenReturn(List.of(new WorkerExportMetrics(7L,null,0L)));
        var types=service.types(admin);
        assertEquals("故障分析_2026-10-01.xlsx",types.filename());
        try(var book=new XSSFWorkbook(new ByteArrayInputStream(types.bytes()))) {
            var sheet=book.getSheetAt(0);
            assertEquals("故障类型",sheet.getRow(0).getCell(0).getStringCellValue());
            assertEquals(3,sheet.getRow(1).getCell(1).getNumericCellValue());
            assertEquals(0.75,sheet.getRow(1).getCell(2).getNumericCellValue());
            assertEquals(1,sheet.getPaneInformation().getHorizontalSplitPosition());
            assertTrue(sheet.getColumnWidth(0)>0);
        }
        try(var book=new XSSFWorkbook(new ByteArrayInputStream(service.workers(admin).bytes()))) {
            var row=book.getSheetAt(0).getRow(1);
            assertEquals("王师傅",row.getCell(0).getStringCellValue());
            assertEquals(2,row.getCell(1).getNumericCellValue());
            assertEquals(4.5,row.getCell(2).getNumericCellValue());
            assertEquals(CellType.BLANK,row.getCell(3).getCellType());
            assertEquals(0,row.getCell(4).getNumericCellValue());
            assertEquals(1,row.getCell(5).getNumericCellValue());
        }
    }

    @Test void orderReportKeepsIdentifiersTextDatesAndLiteralUserInput() throws Exception {
        when(mapper.maxOrderId()).thenReturn(9007199254740993L);
        var created=LocalDateTime.of(2026,10,1,9,0);
        when(mapper.orders(0,9007199254740993L,500)).thenReturn(List.of(new OrderExportRow(
                9007199254740993L,"=SUM(1,1)","电路","宿舍 · 301","HIGH","ASSIGNED",7L,created,"王师傅",created,null,null)));
        when(mapper.orders(9007199254740993L,9007199254740993L,500)).thenReturn(List.of());
        try(var book=new XSSFWorkbook(new ByteArrayInputStream(service.orders(admin).bytes()))) {
            var row=book.getSheetAt(0).getRow(1);
            assertEquals("9007199254740993",row.getCell(0).getStringCellValue());
            assertEquals(CellType.STRING,row.getCell(1).getCellType());
            assertEquals("=SUM(1,1)",row.getCell(1).getStringCellValue());
            assertEquals("待开工",row.getCell(5).getStringCellValue());
            assertEquals(created,row.getCell(7).getLocalDateTimeCellValue());
            assertEquals(CellType.BLANK,row.getCell(8).getCellType());
            assertEquals(CellType.BLANK,row.getCell(9).getCellType());
        }
    }

    @Test void allReportsRejectNonAdminBeforeReadingData() {
        var student=new UserVO(1,"student001","学生",null,UserRole.STUDENT);
        for(Runnable action:List.<Runnable>of(()->service.orders(student),()->service.workers(student),()->service.types(student))) {
            assertEquals(ErrorCode.FORBIDDEN,assertThrows(BusinessException.class,action::run).getErrorCode());
        }
        verifyNoInteractions(mapper,statisticsMapper);
    }
}

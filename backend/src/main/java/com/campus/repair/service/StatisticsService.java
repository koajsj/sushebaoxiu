package com.campus.repair.service;

import com.campus.repair.mapper.StatisticsMapper;
import com.campus.repair.vo.UserVO;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@lombok.RequiredArgsConstructor
@Transactional(readOnly=true)
public class StatisticsService {
    private final StatisticsMapper statistics;
    private final OrderAccessService access;
    private final Clock clock;
    private LocalDate today() { return LocalDate.ofInstant(clock.instant(), ZoneId.of("Asia/Shanghai")); }
    private static long number(Object value) { return value == null ? 0 : ((Number)value).longValue(); }
    private static LocalDate day(Object value) {
        if(value instanceof LocalDate date) return date;
        if(value instanceof java.sql.Date date) return date.toLocalDate();
        return LocalDate.parse(value.toString());
    }

    public Map<String,Object> overview(UserVO user) {
        access.requireAdmin(user);
        var start=today().atStartOfDay();
        var counts=statistics.overviewCounts(start,start.plusDays(1));
        long total=number(counts.get("total")),completed=number(counts.get("completed"));
        Double seconds=statistics.averageRepairSeconds();
        var result=new LinkedHashMap<String,Object>();
        result.put("todayCount",number(counts.get("today")));
        result.put("activeCount",number(counts.get("active")));
        result.put("completionRate",total==0?null:BigDecimal.valueOf(completed*100.0/total).setScale(1,RoundingMode.HALF_UP));
        result.put("averageRepairHours",seconds==null?null:BigDecimal.valueOf(seconds/3600).setScale(1,RoundingMode.HALF_UP));
        result.put("totalCount",total);
        result.put("waitingAuditCount",number(counts.get("waitingAudit")));
        result.put("reworkPendingCount",number(counts.get("reworkPending")));
        result.put("overdueCount",number(counts.get("overdue")));result.put("reworkCount",number(counts.get("rework")));
        return result;
    }
    public List<Map<String,Object>> trend(UserVO user) {
        access.requireAdmin(user);
        var start=today().minusDays(13);
        Map<LocalDate,Long> created=new HashMap<>(),finished=new HashMap<>();
        statistics.createdTrend(start.atStartOfDay()).forEach(row->created.put(day(row.get("day")),number(row.get("amount"))));
        statistics.finishedTrend(start.atStartOfDay()).forEach(row->finished.put(day(row.get("day")),number(row.get("amount"))));
        var result=new ArrayList<Map<String,Object>>();
        for(int offset=0;offset<14;offset++) {
            var date=start.plusDays(offset);
            result.add(Map.of("date",date.toString(),"created",created.getOrDefault(date,0L),"finished",finished.getOrDefault(date,0L)));
        }
        return result;
    }
    public List<Map<String,Object>> types(UserVO user) {
        access.requireAdmin(user);
        var rows=statistics.typeCounts();
        long total=rows.stream().mapToLong(row->number(row.get("amount"))).sum();
        return rows.stream().map(row->{var item=new LinkedHashMap<String,Object>();
            item.put("typeId",number(row.get("typeId")));item.put("typeName",row.get("typeName"));item.put("count",number(row.get("amount")));
            item.put("percentage",total==0?null:BigDecimal.valueOf(number(row.get("amount"))*100.0/total).setScale(1,RoundingMode.HALF_UP));
            return (Map<String,Object>)item;}).toList();
    }
    public List<Map<String,Object>> workers(UserVO user) {
        access.requireAdmin(user);
        return statistics.workerCounts().stream().map(row->{var item=new LinkedHashMap<String,Object>();
            item.put("workerId",number(row.get("workerId")));item.put("workerName",row.get("workerName"));
            item.put("completedCount",number(row.get("completedCount")));item.put("activeCount",number(row.get("activeCount")));
            item.put("averageRating",row.get("averageRating")==null?null:
                    BigDecimal.valueOf(((Number)row.get("averageRating")).doubleValue()).setScale(1,RoundingMode.HALF_UP));
            return (Map<String,Object>)item;}).toList();
    }
}

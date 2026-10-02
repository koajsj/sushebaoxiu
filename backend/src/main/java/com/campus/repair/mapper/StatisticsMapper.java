package com.campus.repair.mapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface StatisticsMapper {
    String REPAIR_DURATION_SECONDS = "TIMESTAMPDIFF(SECOND,MIN(start_time),MAX(finish_time))";
    @Select("""
        SELECT COUNT(*) AS total,
          COALESCE(SUM(create_time >= #{start} AND create_time < #{end}),0) AS today,
          COALESCE(SUM((status='WAIT_ASSIGN' AND worker_id IS NOT NULL) OR status IN ('ASSIGNED','PROCESSING','WAIT_CONFIRM','REWORK_PENDING')),0) AS active,
          COALESCE(SUM(status='WAIT_AUDIT'),0) AS waitingAudit,
          COALESCE(SUM(status='REWORK_PENDING'),0) AS reworkPending,
          COALESCE(SUM(status IN ('FINISHED','COMMENTED')),0) AS completed,
          COALESCE(SUM(overdue_type IS NOT NULL),0) AS overdue,
          COALESCE(SUM(repair_round>1 OR status='REWORK_PENDING'),0) AS rework
        FROM repair_order
        """)
    Map<String,Object> overviewCounts(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Select("SELECT AVG(duration_seconds) FROM (SELECT " + REPAIR_DURATION_SECONDS + " AS duration_seconds FROM repair_record WHERE finish_time IS NOT NULL GROUP BY order_id,round_no) repairs")
    Double averageRepairSeconds();

    @Select("SELECT DATE(create_time) AS day,COUNT(*) AS amount FROM repair_order WHERE create_time >= #{start} GROUP BY DATE(create_time) ORDER BY day")
    List<Map<String,Object>> createdTrend(@Param("start") LocalDateTime start);

    @Select("SELECT DATE(create_time) AS day,COUNT(*) AS amount FROM order_event WHERE action='FINISH' AND create_time >= #{start} GROUP BY DATE(create_time) ORDER BY day")
    List<Map<String,Object>> finishedTrend(@Param("start") LocalDateTime start);

    @Select("SELECT t.id AS typeId,t.name AS typeName,COUNT(o.id) AS amount FROM repair_type t LEFT JOIN repair_order o ON o.type_id=t.id GROUP BY t.id,t.name ORDER BY t.id")
    List<Map<String,Object>> typeCounts();

    @Select("SELECT w.id AS workerId,u.real_name AS workerName,COALESCE(done.amount,0) AS completedCount,ratings.averageRating AS averageRating,COALESCE(active.amount,0) AS activeCount FROM worker w JOIN `user` u ON u.id=w.user_id LEFT JOIN (SELECT worker_id,COUNT(*) AS amount FROM repair_order WHERE status IN ('FINISHED','COMMENTED') GROUP BY worker_id) done ON done.worker_id=w.id LEFT JOIN (SELECT o.worker_id,AVG(e.score) AS averageRating FROM evaluation e JOIN repair_order o ON o.id=e.order_id GROUP BY o.worker_id) ratings ON ratings.worker_id=w.id LEFT JOIN (SELECT worker_id,COUNT(*) AS amount FROM repair_order WHERE status IN ('WAIT_ASSIGN','ASSIGNED','PROCESSING') GROUP BY worker_id) active ON active.worker_id=w.id ORDER BY w.id")
    List<Map<String,Object>> workerCounts();
}

package com.campus.repair.mapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface StatisticsMapper {
    @Select("SELECT COUNT(*) FROM repair_order WHERE create_time >= #{start} AND create_time < #{end}")
    long createdBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Select("SELECT COUNT(*) FROM repair_order WHERE status IN ('ASSIGNED','PROCESSING','WAIT_CONFIRM')")
    long activeCount();

    @Select("SELECT COUNT(*) FROM repair_order")
    long totalCount();

    @Select("SELECT COUNT(*) FROM repair_order WHERE status IN ('FINISHED','COMMENTED')")
    long completedCount();

    @Select("SELECT AVG(TIMESTAMPDIFF(SECOND,s.create_time,f.create_time)) FROM order_event s JOIN order_event f ON f.order_id=s.order_id AND f.action='FINISH' WHERE s.action='START' AND f.create_time>=s.create_time")
    Double averageRepairSeconds();

    @Select("SELECT DATE(create_time) AS day,COUNT(*) AS amount FROM repair_order WHERE create_time >= #{start} GROUP BY DATE(create_time) ORDER BY day")
    List<Map<String,Object>> createdTrend(@Param("start") LocalDateTime start);

    @Select("SELECT DATE(create_time) AS day,COUNT(*) AS amount FROM order_event WHERE action='FINISH' AND create_time >= #{start} GROUP BY DATE(create_time) ORDER BY day")
    List<Map<String,Object>> finishedTrend(@Param("start") LocalDateTime start);

    @Select("SELECT t.id AS typeId,t.name AS typeName,COUNT(o.id) AS amount FROM repair_type t LEFT JOIN repair_order o ON o.type_id=t.id GROUP BY t.id,t.name ORDER BY t.id")
    List<Map<String,Object>> typeCounts();

    @Select("SELECT w.id AS workerId,u.real_name AS workerName,COALESCE(done.amount,0) AS completedCount,COALESCE(ratings.averageRating,0) AS averageRating,COALESCE(active.amount,0) AS activeCount FROM worker w JOIN `user` u ON u.id=w.user_id LEFT JOIN (SELECT worker_id,COUNT(*) AS amount FROM repair_order WHERE status IN ('FINISHED','COMMENTED') GROUP BY worker_id) done ON done.worker_id=w.id LEFT JOIN (SELECT o.worker_id,AVG(e.score) AS averageRating FROM evaluation e JOIN repair_order o ON o.id=e.order_id GROUP BY o.worker_id) ratings ON ratings.worker_id=w.id LEFT JOIN (SELECT worker_id,COUNT(*) AS amount FROM repair_order WHERE status IN ('WAIT_ASSIGN','ASSIGNED','PROCESSING') GROUP BY worker_id) active ON active.worker_id=w.id ORDER BY w.id")
    List<Map<String,Object>> workerCounts();
}

package com.campus.repair.mapper;

import com.campus.repair.vo.OrderExportRow;
import com.campus.repair.vo.WorkerExportMetrics;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ExportMapper {
    @Select("SELECT COALESCE(MAX(id),0) FROM repair_order")
    long maxOrderId();

    // Limit the parent page first, then aggregate only its records/events. No per-order queries.
    @Select("""
        WITH page AS (
          SELECT id,student_id,type_id,building_id,room_no,priority,status,worker_id,accepted_time,create_time
          FROM repair_order WHERE id > #{afterId} AND id <= #{maxId} ORDER BY id LIMIT #{size}
        ), rounds AS (
          SELECT order_id,round_no,
        """ + StatisticsMapper.REPAIR_DURATION_SECONDS + """
          AS seconds FROM repair_record
          WHERE finish_time IS NOT NULL AND order_id IN (SELECT id FROM page) GROUP BY order_id,round_no
        ), durations AS (
          SELECT order_id,SUM(seconds) AS repairSeconds FROM rounds GROUP BY order_id
        ), confirmations AS (
          SELECT order_id,MAX(create_time) AS completeTime FROM order_event
          WHERE action='CONFIRM' AND order_id IN (SELECT id FROM page) GROUP BY order_id
        )
        SELECT o.id,su.real_name AS studentName,t.name AS typeName,
          CONCAT(b.name,' · ',o.room_no) AS location,o.priority,o.status,o.worker_id AS workerId,
          o.accepted_time AS acceptedTime,wu.real_name AS workerName,o.create_time AS createTime,
          CASE WHEN o.status IN ('FINISHED','COMMENTED') THEN c.completeTime ELSE NULL END AS completeTime,
          d.repairSeconds
        FROM page o JOIN student s ON s.id=o.student_id JOIN `user` su ON su.id=s.user_id
        JOIN repair_type t ON t.id=o.type_id JOIN building b ON b.id=o.building_id
        LEFT JOIN worker w ON w.id=o.worker_id LEFT JOIN `user` wu ON wu.id=w.user_id
        LEFT JOIN durations d ON d.order_id=o.id LEFT JOIN confirmations c ON c.order_id=o.id ORDER BY o.id
        """)
    List<OrderExportRow> orders(@Param("afterId") long afterId, @Param("maxId") long maxId, @Param("size") int size);

    // Extra export metrics; Dashboard counts/ratings come from StatisticsService unchanged.
    @Select("""
        WITH rounds AS (
          SELECT order_id,round_no,MAX(id) AS lastRecordId,
        """ + StatisticsMapper.REPAIR_DURATION_SECONDS + """
          AS seconds FROM repair_record WHERE finish_time IS NOT NULL GROUP BY order_id,round_no
        ), durations AS (
          SELECT r.worker_id,AVG(rounds.seconds) AS averageRepairSeconds
          FROM rounds JOIN repair_record r ON r.id=rounds.lastRecordId GROUP BY r.worker_id
        ), reworks AS (
          SELECT worker_id,COUNT(*) AS reworkCount FROM order_event
          WHERE action='ACCEPTANCE_FAIL' GROUP BY worker_id
        )
        SELECT w.id AS workerId,d.averageRepairSeconds,COALESCE(r.reworkCount,0) AS reworkCount
        FROM worker w LEFT JOIN durations d ON d.worker_id=w.id LEFT JOIN reworks r ON r.worker_id=w.id ORDER BY w.id
        """)
    List<WorkerExportMetrics> workerMetrics();
}

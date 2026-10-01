package com.campus.repair.mapper;

import com.campus.repair.entity.RepairOrderEntity;

@org.apache.ibatis.annotations.Mapper
public interface RepairOrderMapper extends com.baomidou.mybatisplus.core.mapper.BaseMapper<RepairOrderEntity> {
    @org.apache.ibatis.annotations.Select("SELECT worker_id, COUNT(*) AS active_count FROM repair_order WHERE worker_id IS NOT NULL AND status IN ('WAIT_ASSIGN','ASSIGNED','PROCESSING') GROUP BY worker_id")
    java.util.List<com.campus.repair.vo.WorkerLoadVO> activeLoads();

    @org.apache.ibatis.annotations.Select("SELECT COUNT(*) FROM repair_order o WHERE o.worker_id = #{workerId} AND EXISTS (SELECT 1 FROM order_event e WHERE e.order_id = o.id AND e.action = 'ASSIGN' AND e.create_time >= #{start})")
    long countAssignedToday(@org.apache.ibatis.annotations.Param("workerId") long workerId,
            @org.apache.ibatis.annotations.Param("start") java.time.LocalDateTime start);

    @org.apache.ibatis.annotations.Select("""
        <script>
        SELECT COUNT(*) AS total,
          COALESCE(SUM(status IN ('WAIT_AUDIT','WAIT_ASSIGN','ASSIGNED')),0) AS pending,
          COALESCE(SUM(status IN ('PROCESSING','WAIT_CONFIRM','REWORK_PENDING')),0) AS active,
          COALESCE(SUM(status IN ('FINISHED','COMMENTED')),0) AS completed,
          COALESCE(SUM(create_time &gt;= #{start}),0) AS today
        FROM repair_order
        <where>
          <if test="studentId != null">student_id = #{studentId}</if>
          <if test="workerId != null">worker_id = #{workerId}</if>
        </where>
        </script>
        """)
    java.util.Map<String,Object> summary(@org.apache.ibatis.annotations.Param("studentId") Long studentId,
            @org.apache.ibatis.annotations.Param("workerId") Long workerId,
            @org.apache.ibatis.annotations.Param("start") java.time.LocalDateTime start);

    @org.apache.ibatis.annotations.Select("SELECT * FROM repair_order WHERE id = #{id} FOR UPDATE")
    RepairOrderEntity lockById(long id);
    @org.apache.ibatis.annotations.Select("SELECT id FROM repair_order WHERE id > #{after} AND overdue_type IS NULL AND ((worker_id IS NOT NULL AND accepted_time IS NULL AND status IN ('WAIT_ASSIGN','ASSIGNED') AND response_due_time <= #{now}) OR (status='ASSIGNED' AND accepted_time IS NOT NULL AND started_time IS NULL AND start_due_time <= #{now}) OR (status='PROCESSING' AND repair_due_time <= #{now})) ORDER BY id LIMIT 100")
    java.util.List<Long> overdueCandidates(@org.apache.ibatis.annotations.Param("now") java.time.LocalDateTime now,
            @org.apache.ibatis.annotations.Param("after") long after);

    @org.apache.ibatis.annotations.Select("SELECT COUNT(*) FROM repair_order WHERE worker_id=#{workerId} AND id<>#{orderId} AND status IN ('ASSIGNED','PROCESSING') AND appointment_status='ACCEPTED' AND appointment_start < #{end} AND appointment_end > #{start}")
    long appointmentConflicts(@org.apache.ibatis.annotations.Param("workerId") long workerId,
            @org.apache.ibatis.annotations.Param("orderId") long orderId,
            @org.apache.ibatis.annotations.Param("start") java.time.LocalDateTime start,
            @org.apache.ibatis.annotations.Param("end") java.time.LocalDateTime end);
}

package com.campus.repair.mapper;

import com.campus.repair.entity.RepairOrderEntity;

@org.apache.ibatis.annotations.Mapper
public interface RepairOrderMapper extends com.baomidou.mybatisplus.core.mapper.BaseMapper<RepairOrderEntity> {
    @org.apache.ibatis.annotations.Select("SELECT worker_id, COUNT(*) AS active_count FROM repair_order WHERE worker_id IS NOT NULL AND status IN ('WAIT_ASSIGN','ASSIGNED','PROCESSING') GROUP BY worker_id")
    java.util.List<com.campus.repair.vo.WorkerLoadVO> activeLoads();

    @org.apache.ibatis.annotations.Select("SELECT COUNT(*) FROM repair_order o WHERE o.worker_id = #{workerId} AND EXISTS (SELECT 1 FROM order_event e WHERE e.order_id = o.id AND e.action = 'ASSIGN' AND e.create_time >= #{start})")
    long countAssignedToday(@org.apache.ibatis.annotations.Param("workerId") long workerId,
            @org.apache.ibatis.annotations.Param("start") java.time.LocalDateTime start);

    @org.apache.ibatis.annotations.Select("SELECT * FROM repair_order WHERE id = #{id} FOR UPDATE")
    RepairOrderEntity lockById(long id);
}

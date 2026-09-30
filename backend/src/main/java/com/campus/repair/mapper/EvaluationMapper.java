package com.campus.repair.mapper;

import com.campus.repair.entity.EvaluationEntity;

@org.apache.ibatis.annotations.Mapper
public interface EvaluationMapper extends com.baomidou.mybatisplus.core.mapper.BaseMapper<EvaluationEntity> {
    @org.apache.ibatis.annotations.Select("""
            SELECT AVG(CAST(e.score AS DECIMAL(20,12))) FROM evaluation e
            JOIN repair_order o ON o.id = e.order_id WHERE o.worker_id = #{workerId}
            """)
    java.math.BigDecimal averageForWorker(@org.apache.ibatis.annotations.Param("workerId") long workerId);
}

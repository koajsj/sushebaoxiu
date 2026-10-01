package com.campus.repair.mapper;

import com.campus.repair.entity.RepairImageEntity;

@org.apache.ibatis.annotations.Mapper
public interface RepairImageMapper extends com.baomidou.mybatisplus.core.mapper.BaseMapper<RepairImageEntity> {
    @org.apache.ibatis.annotations.Select("SELECT * FROM repair_image WHERE id=#{id} FOR UPDATE")
    RepairImageEntity lockById(@org.apache.ibatis.annotations.Param("id") String id);
    @org.apache.ibatis.annotations.Select("SELECT id FROM repair_image WHERE order_id IS NULL AND create_time < #{cutoff} AND id > #{after} ORDER BY id LIMIT 100")
    java.util.List<String> oldUnbound(@org.apache.ibatis.annotations.Param("cutoff") java.time.LocalDateTime cutoff,
            @org.apache.ibatis.annotations.Param("after") String after);
    @org.apache.ibatis.annotations.Select("SELECT (SELECT COUNT(*) FROM repair_order WHERE image_url=CONCAT('/api/images/',#{id})) + (SELECT COUNT(*) FROM repair_record WHERE image_url=CONCAT('/api/images/',#{id}))")
    long referenceCount(@org.apache.ibatis.annotations.Param("id") String id);
    @org.apache.ibatis.annotations.Select("SELECT COUNT(*) FROM repair_image WHERE owner_id=#{userId} AND order_id IS NULL")
    long unboundCount(@org.apache.ibatis.annotations.Param("userId") long userId);
}

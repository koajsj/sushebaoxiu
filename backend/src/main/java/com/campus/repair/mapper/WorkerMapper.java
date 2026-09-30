package com.campus.repair.mapper;

import com.campus.repair.entity.WorkerEntity;

@org.apache.ibatis.annotations.Mapper
public interface WorkerMapper extends com.baomidou.mybatisplus.core.mapper.BaseMapper<WorkerEntity> {
    @org.apache.ibatis.annotations.Update("UPDATE worker SET task_count = task_count + 1 WHERE id = #{id}")
    int incrementTasks(long id);
    @org.apache.ibatis.annotations.Select("SELECT * FROM worker WHERE id = #{id} FOR UPDATE")
    WorkerEntity lockById(long id);
}

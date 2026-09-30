package com.campus.repair.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/** Infrastructure probe; does not access business tables. */
@Mapper
public interface DatabaseProbeMapper {
    @Select("SELECT 1")
    int checkConnection();
}

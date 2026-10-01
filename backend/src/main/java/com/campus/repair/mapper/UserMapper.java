package com.campus.repair.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.repair.entity.UserEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface UserMapper extends BaseMapper<UserEntity> {
    @org.apache.ibatis.annotations.Select("SELECT * FROM `user` WHERE id=#{id} FOR UPDATE")
    UserEntity lockById(@Param("id") long id);
    @org.apache.ibatis.annotations.Select("SELECT id FROM `user` WHERE role='ADMIN' AND status=1 ORDER BY id FOR UPDATE")
    java.util.List<Long> lockActiveAdmins();
    @Update("UPDATE `user` SET token_version = token_version + 1 WHERE id = #{id}")
    int revokeTokens(@Param("id") long id);
}

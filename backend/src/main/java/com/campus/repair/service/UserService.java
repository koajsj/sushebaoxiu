package com.campus.repair.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.repair.common.BusinessException;
import com.campus.repair.common.ErrorCode;
import com.campus.repair.entity.UserEntity;
import com.campus.repair.mapper.UserMapper;
import org.springframework.stereotype.Service;

@Service
public class UserService {
    private final UserMapper mapper;

    public UserService(UserMapper mapper) {
        this.mapper = mapper;
    }

    public UserEntity findByUsername(String username) {
        return mapper.selectOne(new LambdaQueryWrapper<UserEntity>().eq(UserEntity::getUsername, username));
    }

    public UserEntity findById(long id) {
        return mapper.selectById(id);
    }

    public void revokeTokens(long id) {
        if (mapper.revokeTokens(id) != 1) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
    }
}

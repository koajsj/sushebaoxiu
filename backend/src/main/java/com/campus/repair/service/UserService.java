package com.campus.repair.service;

import com.campus.repair.utils.BusinessTime;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.repair.common.BusinessException;
import com.campus.repair.common.ErrorCode;
import com.campus.repair.entity.UserEntity;
import com.campus.repair.mapper.UserMapper;
import com.campus.repair.dto.ChangePasswordRequest;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;

@Service
public class UserService {
    private final UserMapper mapper;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public UserService(UserMapper mapper,PasswordEncoder passwordEncoder,Clock clock) {
        this.mapper = mapper;this.passwordEncoder=passwordEncoder;this.clock=clock;
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
    @Transactional
    public void changePassword(long id,ChangePasswordRequest input){
        var user=mapper.lockById(id);if(user==null||user.getStatus()!=1)throw new BusinessException(ErrorCode.UNAUTHORIZED);
        if(input.newPassword().getBytes(StandardCharsets.UTF_8).length>72||
                !passwordEncoder.matches(input.oldPassword(),user.getPassword()))
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        user.setPassword(passwordEncoder.encode(input.newPassword()));
        user.setTokenVersion(user.getTokenVersion()+1);
        user.setUpdateTime(BusinessTime.now(clock));
        mapper.updateById(user);
    }
}

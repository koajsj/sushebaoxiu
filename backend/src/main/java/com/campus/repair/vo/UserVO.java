package com.campus.repair.vo;

import com.campus.repair.entity.UserEntity;
import com.campus.repair.security.UserRole;

public record UserVO(long id, String username, String realName, String phone, UserRole role) {
    public static UserVO from(UserEntity user) {
        return new UserVO(user.getId(), user.getUsername(), user.getRealName(), user.getPhone(), user.getRole());
    }
}

package com.campus.repair.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.campus.repair.security.UserRole;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("`user`")
public class UserEntity {
    private Long id;
    private String username;
    @JsonIgnore
    private String password;
    private String realName;
    private String phone;
    private UserRole role;
    private Integer status;
    private Long tokenVersion;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}

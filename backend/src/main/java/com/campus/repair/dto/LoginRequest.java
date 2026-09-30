package com.campus.repair.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LoginRequest {
    @NotBlank(message = "请输入账号")
    @Size(max = 64, message = "账号长度不能超过64位")
    private String username;
    @NotBlank(message = "请输入密码")
    @Size(max = 72, message = "密码长度不能超过72位")
    private String password;
}

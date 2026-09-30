package com.campus.repair.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MessageRequest(@NotBlank(message="请输入消息内容") @Size(max=2000,message="消息最多2000字") String content) {}

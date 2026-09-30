package com.campus.repair.vo;

import java.time.LocalDateTime;

public record MessageVO(long id, long orderId, long senderId, String senderName,
        long receiverId, String content, boolean read, LocalDateTime createTime) {}

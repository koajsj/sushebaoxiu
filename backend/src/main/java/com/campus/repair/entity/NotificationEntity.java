package com.campus.repair.entity;

@lombok.Getter @lombok.Setter
@com.baomidou.mybatisplus.annotation.TableName("notification")
public class NotificationEntity {
    private Long id;
    private Long userId;
    private String title;
    private String content;
    private Integer readStatus;
    private java.time.LocalDateTime createTime;
    private String idempotencyKey;
}

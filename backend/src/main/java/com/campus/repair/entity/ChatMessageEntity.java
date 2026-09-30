package com.campus.repair.entity;

@lombok.Getter @lombok.Setter
@com.baomidou.mybatisplus.annotation.TableName("chat_message")
public class ChatMessageEntity {
    private Long id;
    private Long orderId;
    private Long senderId;
    private Long receiverId;
    private String content;
    private Integer readStatus;
    private java.time.LocalDateTime createTime;
}

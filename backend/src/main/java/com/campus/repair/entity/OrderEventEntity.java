package com.campus.repair.entity;

@lombok.Getter
@lombok.Setter
@com.baomidou.mybatisplus.annotation.TableName("order_event")
public class OrderEventEntity {
    private Long id;
    private Long orderId;
    private Long actorId;
    private String action;
    private String status;
    private java.time.LocalDateTime createTime;
    private String content;
    private Integer roundNo;
    private Long workerId;
}

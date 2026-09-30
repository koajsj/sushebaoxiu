package com.campus.repair.entity;

@lombok.Getter
@lombok.Setter
@com.baomidou.mybatisplus.annotation.TableName("repair_image")
public class RepairImageEntity {
    @com.baomidou.mybatisplus.annotation.TableId(type=com.baomidou.mybatisplus.annotation.IdType.INPUT)
    private String id;
    private Long ownerId;
    private Long orderId;
    private String contentType;
    private java.time.LocalDateTime createTime;
}

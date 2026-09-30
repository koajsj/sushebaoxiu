package com.campus.repair.entity;

@lombok.Getter
@lombok.Setter
@com.baomidou.mybatisplus.annotation.TableName("repair_order")
public class RepairOrderEntity {
    private Long id;
    private Long studentId;
    private Long typeId;
    private String title;
    private String description;
    private String imageUrl;
    private Long buildingId;
    private String roomNo;
    private String priority;
    private String status;
    private Long workerId;
    private java.time.LocalDateTime createTime;
    private java.time.LocalDateTime updateTime;
}

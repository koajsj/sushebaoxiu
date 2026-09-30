package com.campus.repair.entity;

@lombok.Getter
@lombok.Setter
@com.baomidou.mybatisplus.annotation.TableName("repair_type")
public class RepairTypeEntity {
    private Long id;
    private String name;
    private String description;
}

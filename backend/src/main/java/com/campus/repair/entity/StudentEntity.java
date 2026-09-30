package com.campus.repair.entity;

@lombok.Getter
@lombok.Setter
@com.baomidou.mybatisplus.annotation.TableName("student")
public class StudentEntity {
    private Long id;
    private Long userId;
    private String studentNo;
    private String college;
    private String className;
    private Long buildingId;
    private String roomNo;
}

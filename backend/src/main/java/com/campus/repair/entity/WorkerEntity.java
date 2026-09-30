package com.campus.repair.entity;

@lombok.Getter
@lombok.Setter
@com.baomidou.mybatisplus.annotation.TableName("worker")
public class WorkerEntity {
    private Long id;
    private Long userId;
    private String skillType;
    private java.math.BigDecimal score;
    private Integer taskCount;
    private Integer status;
    private java.math.BigDecimal longitude;
    private java.math.BigDecimal latitude;
}

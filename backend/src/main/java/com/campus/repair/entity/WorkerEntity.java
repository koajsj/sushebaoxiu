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
    @com.baomidou.mybatisplus.annotation.TableField(updateStrategy=com.baomidou.mybatisplus.annotation.FieldStrategy.ALWAYS)
    private java.math.BigDecimal longitude;
    @com.baomidou.mybatisplus.annotation.TableField(updateStrategy=com.baomidou.mybatisplus.annotation.FieldStrategy.ALWAYS)
    private java.math.BigDecimal latitude;
}

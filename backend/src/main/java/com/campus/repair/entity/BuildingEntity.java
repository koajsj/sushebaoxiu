package com.campus.repair.entity;

@lombok.Getter
@lombok.Setter
@com.baomidou.mybatisplus.annotation.TableName("building")
public class BuildingEntity {
    private Long id;
    private String name;
    private String type;
    private java.math.BigDecimal longitude;
    private java.math.BigDecimal latitude;
}

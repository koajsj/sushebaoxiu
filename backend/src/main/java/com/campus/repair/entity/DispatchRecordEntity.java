package com.campus.repair.entity;

@lombok.Getter
@lombok.Setter
@com.baomidou.mybatisplus.annotation.TableName("dispatch_record")
public class DispatchRecordEntity {
    private Long id;
    private Long orderId;
    private Long workerId;
    private java.math.BigDecimal skillScore;
    private java.math.BigDecimal distanceScore;
    private java.math.BigDecimal loadScore;
    private java.math.BigDecimal ratingScore;
    private java.math.BigDecimal totalScore;
    private String reason;
    private String recommendationBatch;
    private Boolean confirmed;
    private java.time.LocalDateTime createTime;
    private Integer roundNo;
    private String decision;
    private String method;
    private String rejectReason;
    private java.time.LocalDateTime responseTime;
}

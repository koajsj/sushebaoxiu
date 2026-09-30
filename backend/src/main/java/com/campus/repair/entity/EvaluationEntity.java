package com.campus.repair.entity;

@lombok.Getter
@lombok.Setter
@com.baomidou.mybatisplus.annotation.TableName("evaluation")
public class EvaluationEntity {
    private Long id;
    private Long orderId;
    private Long studentId;
    private Integer score;
    private String content;
    private java.time.LocalDateTime createTime;
}

package com.campus.repair.entity;

@lombok.Getter
@lombok.Setter
@com.baomidou.mybatisplus.annotation.TableName("repair_record")
public class RepairRecordEntity {
    private Long id;
    private Long orderId;
    private Long workerId;
    private String content;
    private String imageUrl;
    private java.time.LocalDateTime startTime;
    private java.time.LocalDateTime finishTime;
    private Integer roundNo;
    private String requestKey;
    private String requestHash;
}

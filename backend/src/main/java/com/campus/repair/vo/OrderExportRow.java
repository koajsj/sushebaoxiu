package com.campus.repair.vo;

import java.time.LocalDateTime;

public record OrderExportRow(Long id, String studentName, String typeName, String location,
        String priority, String status, Long workerId, LocalDateTime acceptedTime,
        String workerName, LocalDateTime createTime, LocalDateTime completeTime, Long repairSeconds) {}

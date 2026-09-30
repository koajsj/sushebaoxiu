package com.campus.repair.vo;

import java.math.BigDecimal;
public record MapWorkerVO(Long id, String name, String skillType, String status, long activeTaskCount,
        BigDecimal longitude, BigDecimal latitude) {}

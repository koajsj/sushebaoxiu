package com.campus.repair.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
public record MapOrderVO(Long id, String title, String status, String address, String typeName,
        Long workerId, BigDecimal longitude, BigDecimal latitude, LocalDateTime createTime) {}

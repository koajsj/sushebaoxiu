package com.campus.repair.service;

import com.campus.repair.common.BusinessException;
import com.campus.repair.common.ErrorCode;

public enum OrderStatus {
    CREATED, WAIT_AUDIT, WAIT_ASSIGN, ASSIGNED, PROCESSING, WAIT_CONFIRM, FINISHED, COMMENTED, REJECTED, REWORK_PENDING;

    public static void require(String current, OrderStatus expected) {
        if (!expected.name().equals(current)) throw new BusinessException(ErrorCode.CONFLICT);
    }

    public static OrderStatus filter(String value) {
        if (value == null || value.isBlank()) return null;
        try { return valueOf(value); }
        catch (IllegalArgumentException exception) { throw new BusinessException(ErrorCode.BAD_REQUEST); }
    }
}

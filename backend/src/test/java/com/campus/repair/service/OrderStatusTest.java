package com.campus.repair.service;

import static org.junit.jupiter.api.Assertions.*;
import com.campus.repair.common.BusinessException;
import org.junit.jupiter.api.Test;

class OrderStatusTest {
    @Test void onlyCoreTransitionsAreAllowed() {
        OrderStatus.require("WAIT_AUDIT", OrderStatus.WAIT_AUDIT);
        assertThrows(BusinessException.class, () -> OrderStatus.require("FINISHED", OrderStatus.PROCESSING));
        assertThrows(BusinessException.class, () -> OrderStatus.require("CREATED", OrderStatus.WAIT_AUDIT));
    }
    @Test void invalidFilterIsRejected() {
        assertEquals(OrderStatus.COMMENTED, OrderStatus.filter("COMMENTED"));
        assertNull(OrderStatus.filter(null));
        assertThrows(BusinessException.class, () -> OrderStatus.filter("arbitrary-status"));
    }
}

package com.campus.repair.service;

import com.campus.repair.entity.RepairOrderEntity;

/** Public business stage; legacy statuses stay unchanged in storage. */
public enum OrderPhase {
    CREATED, WAIT_AUDIT, WAIT_DISPATCH, WAIT_ACCEPT, WAIT_START,
    PROCESSING, WAIT_CONFIRM, FINISHED, COMMENTED, REJECTED, REWORK_PENDING;

    public static OrderPhase of(RepairOrderEntity order) {
        if("WAIT_ASSIGN".equals(order.getStatus()))
            return order.getWorkerId()==null?WAIT_DISPATCH:WAIT_ACCEPT;
        if("ASSIGNED".equals(order.getStatus()))
            return order.getAcceptedTime()==null?WAIT_ACCEPT:WAIT_START;
        return valueOf(order.getStatus());
    }
}

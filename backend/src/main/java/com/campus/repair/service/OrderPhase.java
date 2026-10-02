package com.campus.repair.service;

import com.campus.repair.entity.RepairOrderEntity;
import java.time.LocalDateTime;

/** Public business stage; legacy statuses stay unchanged in storage. */
public enum OrderPhase {
    CREATED, WAIT_AUDIT, WAIT_DISPATCH, WAIT_ACCEPT, WAIT_START,
    PROCESSING, WAIT_CONFIRM, FINISHED, COMMENTED, REJECTED, REWORK_PENDING;

    public static OrderPhase of(RepairOrderEntity order) {
        return of(order.getStatus(),order.getWorkerId(),order.getAcceptedTime());
    }

    public static OrderPhase of(String status,Long workerId,LocalDateTime acceptedTime) {
        if("WAIT_ASSIGN".equals(status))
            return workerId==null?WAIT_DISPATCH:WAIT_ACCEPT;
        if("ASSIGNED".equals(status))
            return acceptedTime==null?WAIT_ACCEPT:WAIT_START;
        return valueOf(status);
    }
}

package com.campus.repair.service;

import com.campus.repair.entity.*;
import com.campus.repair.mapper.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@lombok.RequiredArgsConstructor
public class SlaService {
    private final RepairOrderMapper orders;
    private final OrderEventMapper events;
    private final NotificationService notifications;
    private final OrderWorkflowService workflow;
    @Transactional
    public void check(long id) {
        var order=orders.lockById(id);
        if(order==null||order.getOverdueType()!=null)return;
        var now=workflow.now();String type=null;
        if(order.getWorkerId()!=null&&order.getAcceptedTime()==null&&java.util.Set.of("WAIT_ASSIGN","ASSIGNED").contains(order.getStatus())
                &&order.getResponseDueTime()!=null&&!order.getResponseDueTime().isAfter(now))type="RESPONSE";
        if("ASSIGNED".equals(order.getStatus())&&order.getAcceptedTime()!=null&&order.getStartedTime()==null
                &&order.getStartDueTime()!=null&&!order.getStartDueTime().isAfter(now))type="START";
        if("PROCESSING".equals(order.getStatus())&&order.getRepairDueTime()!=null&&!order.getRepairDueTime().isAfter(now))type="REPAIR";
        if(type==null)return;
        order.setOverdueType(type);orders.updateById(order);
        var event=new OrderEventEntity();event.setOrderId(id);event.setActorId(null);event.setAction("SLA_"+type);
        event.setStatus(order.getStatus());event.setContent("系统自动检测："+(type.equals("RESPONSE")?"接单":type.equals("START")?"开工":"维修")+"已超过本轮时限");
        event.setRoundNo(order.getRepairRound());event.setWorkerId(order.getWorkerId());event.setCreateTime(now);events.insert(event);
        notifications.publish(order,event);
    }
}

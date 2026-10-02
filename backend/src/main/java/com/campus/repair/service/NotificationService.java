package com.campus.repair.service;

import com.campus.repair.utils.BusinessTime;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.repair.common.*;
import com.campus.repair.entity.*;
import com.campus.repair.mapper.*;
import com.campus.repair.security.UserRole;
import com.campus.repair.vo.UserVO;
import java.time.*;
import java.util.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@lombok.RequiredArgsConstructor
@lombok.extern.slf4j.Slf4j
public class NotificationService {
    private final NotificationMapper notifications;
    private final StudentMapper students;
    private final WorkerMapper workers;
    private final UserMapper users;
    private final Clock clock;
    private final ApplicationEventPublisher publisher;
    private LocalDateTime now(){return BusinessTime.now(clock);}

    /** Only queue immutable events here. The database write runs after the order commits. */
    public void publish(RepairOrderEntity order,OrderEventEntity event) {
        try {
            onOrderEvent(order,event);
            onWorkflowEvent(order,event);
        } catch (RuntimeException failure) {
            log.error("Notification preparation failed eventType={} businessId={} reason={}",
                    event.getAction(),order.getId(),failure.getClass().getSimpleName());
        }
    }
    private void queue(OrderEventEntity event,long userId,String title,String content) {
        String key=event.getAction()+":"+event.getOrderId()+":"+event.getRoundNo()+":"+event.getId()+":"+userId;
        publisher.publishEvent(new BusinessNotificationEvent(userId,event.getAction(),event.getOrderId(),event.getId(),title,content.strip(),key));
    }
    private void queueAdmins(OrderEventEntity event,String title,String content) {
        users.selectList(new LambdaQueryWrapper<UserEntity>().eq(UserEntity::getRole,UserRole.ADMIN).eq(UserEntity::getStatus,1))
                .forEach(admin->queue(event,admin.getId(),title,content));
    }
    private void onOrderEvent(RepairOrderEntity order,OrderEventEntity event) {
        String action=event.getAction();
        if(!Set.of("SUBMIT","AUDIT","ASSIGN","FINISH").contains(action)) return;
        var student=students.selectById(order.getStudentId());
        if(student==null) throw new BusinessException(ErrorCode.PROFILE_REQUIRED);
        String ref="工单 #"+order.getId();
        switch(action) {
            case "SUBMIT" -> {
                queue(event,student.getUserId(),"报修提交成功",ref+" 已提交，等待审核。 ");
                queueAdmins(event,"新报修待审核",ref+" 已提交，请及时审核。");
            }
            case "AUDIT" -> queue(event,student.getUserId(),"报修审核完成",ref+" 已通过审核，等待派单。");
            case "ASSIGN" -> {
                if(order.getWorkerId()==null) throw new BusinessException(ErrorCode.INVALID_REFERENCE);
                var worker=workers.selectById(order.getWorkerId());
                if(worker==null) throw new BusinessException(ErrorCode.WORKER_UNAVAILABLE);
                queue(event,student.getUserId(),"维修人员已安排",ref+" 已派单。");
                queue(event,worker.getUserId(),"收到新维修任务",ref+" 已分配给你，请查看任务详情。");
            }
            case "FINISH" -> queue(event,student.getUserId(),"维修已完成",ref+" 已提交维修结果，请确认。");
            default -> { }
        }
    }
    private void onWorkflowEvent(RepairOrderEntity order,OrderEventEntity event) {
        String action=event.getAction();String reason=event.getContent();
        if(!Set.of("AUDIT_REJECT","RESUBMIT","WORKER_REJECT","SLA_RESPONSE","SLA_START","SLA_REPAIR","RECALL","ACCEPTANCE_FAIL",
                "REWORK_ORIGINAL","REWORK_REDISPATCH","APPOINTMENT_PROPOSE","APPOINTMENT_ACCEPT","APPOINTMENT_REJECT").contains(action))return;
        var student=students.selectById(order.getStudentId());
        var worker=event.getWorkerId()==null?null:workers.selectById(event.getWorkerId());
        String title=switch(action) {
            case "AUDIT_REJECT" -> "报修审核驳回";case "RESUBMIT" -> "报修重新提交";case "WORKER_REJECT" -> "维修人员拒单";
            case "SLA_RESPONSE" -> "工单接单超时";case "SLA_START" -> "工单待开工超时";case "SLA_REPAIR" -> "工单维修超时";
            case "RECALL" -> "任务已由管理员收回";case "ACCEPTANCE_FAIL" -> "学生验收未通过";
            case "REWORK_ORIGINAL" -> "原维修人员返工";case "REWORK_REDISPATCH" -> "返工等待重新派单";
            case "APPOINTMENT_PROPOSE" -> "维修时间待确认";case "APPOINTMENT_ACCEPT" -> "学生已接受预约";
            default -> "学生拒绝预约";
        };
        String content="工单 #"+order.getId()+" · "+(reason==null?"":reason);
        if(content.codePointCount(0,content.length())>500)content=content.substring(0,content.offsetByCodePoints(0,500));
        if(Set.of("AUDIT_REJECT","RESUBMIT","REWORK_ORIGINAL","REWORK_REDISPATCH","APPOINTMENT_PROPOSE","RECALL").contains(action))queue(event,student.getUserId(),title,content);
        if(worker!=null&&Set.of("ACCEPTANCE_FAIL","REWORK_ORIGINAL","APPOINTMENT_ACCEPT","APPOINTMENT_REJECT","RECALL").contains(action))queue(event,worker.getUserId(),title,content);
        if(Set.of("RESUBMIT","WORKER_REJECT","SLA_RESPONSE","SLA_START","SLA_REPAIR","ACCEPTANCE_FAIL","REWORK_REDISPATCH").contains(action)) {
            queueAdmins(event,title,content);
        }
    }
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public void write(BusinessNotificationEvent event){
        var row=new NotificationEntity();row.setUserId(event.targetUserId());row.setTitle(event.title());row.setContent(event.content());
        row.setIdempotencyKey(event.idempotencyKey());row.setReadStatus(0);row.setCreateTime(now());
        try {
            notifications.insert(row);
        } catch (DuplicateKeyException alreadyDelivered) {
            // The unique event key wins even if the same event is delivered concurrently.
        }
    }
    @Transactional(readOnly=true)
    public Map<String,Object> list(UserVO user,long pageNumber,long size,boolean unreadOnly){
        var query=new LambdaQueryWrapper<NotificationEntity>().eq(NotificationEntity::getUserId,user.id())
                .eq(unreadOnly,NotificationEntity::getReadStatus,0)
                .orderByDesc(NotificationEntity::getCreateTime,NotificationEntity::getId);
        var page=notifications.selectPage(new Page<>(pageNumber,size),query);
        long unread=notifications.selectCount(new LambdaQueryWrapper<NotificationEntity>()
                .eq(NotificationEntity::getUserId,user.id()).eq(NotificationEntity::getReadStatus,0));
        return Map.of("records",page.getRecords(),"total",page.getTotal(),"unreadCount",unread,
                "page",page.getCurrent(),"size",page.getSize(),"latestId",latestNotificationId(user.id()));
    }
    private long latestNotificationId(long userId) {
        var latest=notifications.selectList(new LambdaQueryWrapper<NotificationEntity>().eq(NotificationEntity::getUserId,userId)
                .orderByDesc(NotificationEntity::getId).last("LIMIT 1"));
        return latest.isEmpty()?0L:latest.get(0).getId();
    }
    @Transactional
    public void markRead(UserVO user,long id){
        var row=notifications.selectById(id);
        if(row==null||!row.getUserId().equals(user.id())) throw new BusinessException(ErrorCode.NOT_FOUND);
        if(row.getReadStatus()==0) notifications.update(null,new LambdaUpdateWrapper<NotificationEntity>()
                .eq(NotificationEntity::getId,id).eq(NotificationEntity::getUserId,user.id())
                .eq(NotificationEntity::getReadStatus,0).set(NotificationEntity::getReadStatus,1));
    }
    @Transactional
    public void markAllRead(UserVO user,long throughId){
        long latest=latestNotificationId(user.id());
        if(latest==0)return;
        long cutoff=Math.min(throughId,latest);
        notifications.update(null,new LambdaUpdateWrapper<NotificationEntity>()
                .eq(NotificationEntity::getUserId,user.id()).eq(NotificationEntity::getReadStatus,0)
                .le(NotificationEntity::getId,cutoff).set(NotificationEntity::getReadStatus,1));
    }
}

package com.campus.repair.service;

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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@lombok.RequiredArgsConstructor
public class NotificationService {
    private final NotificationMapper notifications;
    private final StudentMapper students;
    private final WorkerMapper workers;
    private final UserMapper users;
    private final Clock clock;
    private LocalDateTime now(){return LocalDateTime.ofInstant(clock.instant(),ZoneId.of("Asia/Shanghai")).withNano(0);}

    /** Called inside the order transaction so state and its notification commit together. */
    public void onOrderEvent(RepairOrderEntity order,String action) {
        if(!Set.of("SUBMIT","AUDIT","ASSIGN","FINISH").contains(action)) return;
        var student=students.selectById(order.getStudentId());
        if(student==null) throw new BusinessException(ErrorCode.PROFILE_REQUIRED);
        String ref="工单 #"+order.getId();
        switch(action) {
            case "SUBMIT" -> {
                add(student.getUserId(),"报修提交成功",ref+" 已提交，等待审核。 ");
                users.selectList(new LambdaQueryWrapper<UserEntity>().eq(UserEntity::getRole,UserRole.ADMIN).eq(UserEntity::getStatus,1))
                        .forEach(admin->add(admin.getId(),"新报修待审核",ref+" 已提交，请及时审核。"));
            }
            case "AUDIT" -> add(student.getUserId(),"报修审核完成",ref+" 已通过审核，等待派单。");
            case "ASSIGN" -> {
                if(order.getWorkerId()==null) throw new BusinessException(ErrorCode.INVALID_REFERENCE);
                var worker=workers.selectById(order.getWorkerId());
                if(worker==null) throw new BusinessException(ErrorCode.WORKER_UNAVAILABLE);
                add(student.getUserId(),"维修人员已安排",ref+" 已派单。");
                add(worker.getUserId(),"收到新维修任务",ref+" 已分配给你，请查看任务详情。");
            }
            case "FINISH" -> add(student.getUserId(),"维修已完成",ref+" 已提交维修结果，请确认。");
            default -> { }
        }
    }
    private void add(long userId,String title,String content){
        var row=new NotificationEntity();row.setUserId(userId);row.setTitle(title);row.setContent(content.strip());
        row.setReadStatus(0);row.setCreateTime(now());notifications.insert(row);
    }
    @Transactional(readOnly=true)
    public Map<String,Object> list(UserVO user){
        var query=new LambdaQueryWrapper<NotificationEntity>().eq(NotificationEntity::getUserId,user.id())
                .orderByDesc(NotificationEntity::getCreateTime,NotificationEntity::getId);
        var page=notifications.selectPage(new Page<>(1,50),query);
        long unread=notifications.selectCount(new LambdaQueryWrapper<NotificationEntity>()
                .eq(NotificationEntity::getUserId,user.id()).eq(NotificationEntity::getReadStatus,0));
        return Map.of("records",page.getRecords(),"total",page.getTotal(),"unreadCount",unread);
    }
    @Transactional
    public void markRead(UserVO user,long id){
        var row=notifications.selectById(id);
        if(row==null||!row.getUserId().equals(user.id())) throw new BusinessException(ErrorCode.NOT_FOUND);
        if(row.getReadStatus()==0) notifications.update(null,new LambdaUpdateWrapper<NotificationEntity>()
                .eq(NotificationEntity::getId,id).eq(NotificationEntity::getUserId,user.id())
                .eq(NotificationEntity::getReadStatus,0).set(NotificationEntity::getReadStatus,1));
    }
}

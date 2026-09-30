package com.campus.repair.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.campus.repair.common.*;
import com.campus.repair.config.SlaProperties;
import com.campus.repair.dto.*;
import com.campus.repair.entity.*;
import com.campus.repair.mapper.*;
import com.campus.repair.vo.UserVO;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Incremental exception/collaboration flows; every mutation holds the same order row lock. */
@Service
@lombok.RequiredArgsConstructor
public class OrderWorkflowService {
    private final RepairOrderMapper orders;
    private final OrderEventMapper events;
    private final DispatchRecordMapper dispatches;
    private final WorkerMapper workers;
    private final UserMapper users;
    private final BuildingMapper buildings;
    private final RepairTypeMapper types;
    private final OrderAccessService access;
    private final ImageService images;
    private final NotificationService notifications;
    private final SlaProperties sla;
    private final Clock clock;
    public LocalDateTime now() { return LocalDateTime.ofInstant(clock.instant(),ZoneId.of("Asia/Shanghai")).withNano(0); }
    private RepairOrderEntity locked(long id) {
        var order=orders.lockById(id);
        if(order==null)throw new BusinessException(ErrorCode.NOT_FOUND);
        return order;
    }
    public void emit(RepairOrderEntity order,UserVO user,String action,String content) {
        var row=new OrderEventEntity();row.setOrderId(order.getId());row.setActorId(user.id());row.setAction(action);
        row.setStatus(order.getStatus());row.setCreateTime(now());row.setContent(content);
        row.setRoundNo(order.getRepairRound());row.setWorkerId(order.getWorkerId());events.insert(row);
        notifications.publish(order,row);
    }
    private void save(RepairOrderEntity order) { order.setUpdateTime(now());orders.updateById(order); }
    private void clearAppointment(RepairOrderEntity order) {
        order.setAppointmentStart(null);order.setAppointmentEnd(null);order.setAppointmentStatus("NONE");
        order.setAppointmentReason(null);order.setAppointmentVersion(order.getAppointmentVersion()+1);
    }
    private void clearTiming(RepairOrderEntity order) {
        order.setAssignedTime(null);order.setAcceptedTime(null);order.setStartedTime(null);
        order.setResponseDueTime(null);order.setRepairDueTime(null);order.setOverdueType(null);clearAppointment(order);
    }
    public void assigned(RepairOrderEntity order,UserVO user,DispatchRecordEntity smartRecord) {
        order.setDispatchRound(order.getDispatchRound()+1);clearTiming(order);
        order.setAssignedTime(now());order.setResponseDueTime(now().plus(sla.forPriority(order.getPriority()).getResponse()));
        if(smartRecord==null) {
            var row=new DispatchRecordEntity();row.setOrderId(order.getId());row.setWorkerId(order.getWorkerId());
            row.setReason("管理员人工指定，未使用评分模型");row.setRecommendationBatch(UUID.randomUUID().toString());
            row.setConfirmed(true);row.setCreateTime(now());row.setRoundNo(order.getDispatchRound());
            row.setMethod("MANUAL");row.setDecision("ASSIGNED");dispatches.insert(row);
        }
        save(order);emit(order,user,"ASSIGN",(smartRecord==null?"人工":"智能")+"派单 · 第"+order.getDispatchRound()+"轮，维修员 #"+order.getWorkerId());
    }
    public void accepted(RepairOrderEntity order,UserVO user) {
        if(order.getAcceptedTime()!=null||!Set.of("WAIT_ASSIGN","ASSIGNED").contains(order.getStatus()))
            throw new BusinessException(ErrorCode.CONFLICT);
        order.setAcceptedTime(now());order.setResponseDueTime(null);order.setOverdueType(null);order.setStatus("ASSIGNED");
        dispatches.update(null,new LambdaUpdateWrapper<DispatchRecordEntity>().eq(DispatchRecordEntity::getOrderId,order.getId())
                .eq(DispatchRecordEntity::getRoundNo,order.getDispatchRound()).eq(DispatchRecordEntity::getConfirmed,true)
                .eq(DispatchRecordEntity::getDecision,"ASSIGNED").set(DispatchRecordEntity::getDecision,"ACCEPTED")
                .set(DispatchRecordEntity::getResponseTime,now()));
        save(order);emit(order,user,"ACCEPT","维修人员已接受任务");
    }
    public void started(RepairOrderEntity order) {
        if(order.getAcceptedTime()==null)throw new BusinessException(ErrorCode.CONFLICT);
        order.setStartedTime(now());order.setRepairDueTime(now().plus(sla.forPriority(order.getPriority()).getRepair()));
        order.setResponseDueTime(null);order.setOverdueType(null);
    }
    @Transactional
    public void rejectAudit(UserVO user,long id,String reason) {
        access.requireAdmin(user);var order=locked(id);OrderStatus.require(order.getStatus(),OrderStatus.WAIT_AUDIT);
        order.setStatus("REJECTED");save(order);emit(order,user,"AUDIT_REJECT",reason.strip());
    }
    @Transactional
    public void resubmit(UserVO user,long id,CreateOrderRequest input) {
        var order=locked(id);access.requireStudentOwner(user,order);OrderStatus.require(order.getStatus(),OrderStatus.REJECTED);
        if(types.selectById(input.typeId())==null||buildings.selectById(input.buildingId())==null)throw new BusinessException(ErrorCode.INVALID_REFERENCE);
        order.setTitle(input.title().strip());order.setDescription(input.description().strip());order.setTypeId(input.typeId());
        order.setBuildingId(input.buildingId());order.setRoomNo(input.roomNo().strip());order.setPriority(input.priority());
        // The existing image is already bound. Only a different new upload needs binding.
        if(!Objects.equals(order.getImageUrl(),input.imageUrl()))order.setImageUrl(images.bind(user,input.imageUrl(),id));
        emit(order,user,"EDIT","学生修改报修信息");order.setStatus("WAIT_AUDIT");save(order);emit(order,user,"RESUBMIT","修改完成，重新提交审核");
    }
    @Transactional
    public void refuse(UserVO user,long id,String reason) {
        var order=locked(id);var worker=access.requireWorkerOwner(user,order);workers.lockById(worker.getId());
        if(order.getAcceptedTime()!=null||!Set.of("WAIT_ASSIGN","ASSIGNED").contains(order.getStatus()))throw new BusinessException(ErrorCode.CONFLICT);
        dispatches.update(null,new LambdaUpdateWrapper<DispatchRecordEntity>().eq(DispatchRecordEntity::getOrderId,id)
                .eq(DispatchRecordEntity::getRoundNo,order.getDispatchRound()).eq(DispatchRecordEntity::getConfirmed,true)
                .eq(DispatchRecordEntity::getDecision,"ASSIGNED").set(DispatchRecordEntity::getDecision,"REJECTED")
                .set(DispatchRecordEntity::getRejectReason,reason.strip()).set(DispatchRecordEntity::getResponseTime,now()));
        order.setStatus("WAIT_ASSIGN");emit(order,user,"WORKER_REJECT",reason.strip());
        order.setWorkerId(null);clearTiming(order);save(order);
    }
    @Transactional
    public void failAcceptance(UserVO user,long id,String reason) {
        var order=locked(id);access.requireStudentOwner(user,order);OrderStatus.require(order.getStatus(),OrderStatus.WAIT_CONFIRM);
        order.setStatus("REWORK_PENDING");order.setRepairDueTime(null);order.setOverdueType(null);
        save(order);emit(order,user,"ACCEPTANCE_FAIL",reason.strip());
    }
    @Transactional
    public void rework(UserVO user,long id,String mode) {
        access.requireAdmin(user);var order=locked(id);OrderStatus.require(order.getStatus(),OrderStatus.REWORK_PENDING);
        long previousWorker=order.getWorkerId();
        var previous=workers.lockById(previousWorker);
        if("ORIGINAL".equals(mode))access.requireAvailable(previous);
        // Close the previous assignment history without changing its original scores or worker.
        dispatches.update(null,new LambdaUpdateWrapper<DispatchRecordEntity>().eq(DispatchRecordEntity::getOrderId,id)
                .eq(DispatchRecordEntity::getRoundNo,order.getDispatchRound()).eq(DispatchRecordEntity::getConfirmed,true)
                .set(DispatchRecordEntity::getDecision,"REWORK"));
        order.setRepairRound(order.getRepairRound()+1);clearTiming(order);
        if("ORIGINAL".equals(mode)) {
            order.setStatus("ASSIGNED");order.setAcceptedTime(now());order.setAssignedTime(now());save(order);
            emit(order,user,"REWORK_ORIGINAL","原维修员 #"+previousWorker+" 继续返工 · 第"+order.getRepairRound()+"次维修");
        } else {
            order.setStatus("WAIT_ASSIGN");emit(order,user,"REWORK_REDISPATCH","原维修员 #"+previousWorker+"，安排重新派单 · 第"+order.getRepairRound()+"次维修");
            order.setWorkerId(null);save(order);
        }
    }
    @Transactional
    public void propose(UserVO user,long id,AppointmentRequest input) {
        var order=locked(id);access.requireWorkerOwner(user,order);
        if(order.getAcceptedTime()==null||!Set.of("ASSIGNED","PROCESSING").contains(order.getStatus()))throw new BusinessException(ErrorCode.CONFLICT);
        if(!Objects.equals(order.getAppointmentVersion(),input.version()))throw new BusinessException(ErrorCode.CONFLICT);
        if(!input.start().isAfter(now())||!input.end().isAfter(input.start())||Duration.between(input.start(),input.end()).compareTo(Duration.ofHours(24))>0)
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        order.setAppointmentStart(input.start());order.setAppointmentEnd(input.end());order.setAppointmentStatus("PROPOSED");
        order.setAppointmentReason(null);order.setAppointmentVersion(order.getAppointmentVersion()+1);save(order);
        emit(order,user,"APPOINTMENT_PROPOSE",input.start()+" 至 "+input.end()+"（校园时间）");
    }
    @Transactional
    public void respond(UserVO user,long id,AppointmentResponse input) {
        var order=locked(id);access.requireStudentOwner(user,order);
        if(!Set.of("ASSIGNED","PROCESSING").contains(order.getStatus())||!"PROPOSED".equals(order.getAppointmentStatus())
                ||!Objects.equals(order.getAppointmentVersion(),input.version())||!order.getAppointmentEnd().isAfter(now()))throw new BusinessException(ErrorCode.CONFLICT);
        if(!input.accepted()&&(input.reason()==null||input.reason().isBlank()))throw new BusinessException(ErrorCode.BAD_REQUEST);
        order.setAppointmentStatus(input.accepted()?"ACCEPTED":"REJECTED");order.setAppointmentReason(input.accepted()?null:input.reason().strip());
        order.setAppointmentVersion(order.getAppointmentVersion()+1);save(order);
        emit(order,user,input.accepted()?"APPOINTMENT_ACCEPT":"APPOINTMENT_REJECT",input.accepted()?"学生确认预约时间":input.reason().strip());
    }
    public List<Map<String,Object>> history(long id) {
        var rows=dispatches.selectList(new LambdaQueryWrapper<DispatchRecordEntity>().eq(DispatchRecordEntity::getOrderId,id)
                .eq(DispatchRecordEntity::getConfirmed,true).orderByAsc(DispatchRecordEntity::getId));
        if(rows.isEmpty())return List.of();
        var staff=workers.selectByIds(rows.stream().map(DispatchRecordEntity::getWorkerId).distinct().toList());
        var accounts=new HashMap<Long,String>();users.selectByIds(staff.stream().map(WorkerEntity::getUserId).distinct().toList())
                .forEach(u->accounts.put(u.getId(),u.getRealName()));
        var names=new HashMap<Long,String>();staff.forEach(w->names.put(w.getId(),accounts.get(w.getUserId())));
        return rows.stream().map(row->{var v=new LinkedHashMap<String,Object>();v.put("id",row.getId());v.put("workerId",row.getWorkerId());
            v.put("workerName",names.get(row.getWorkerId()));v.put("roundNo",row.getRoundNo());v.put("method",row.getMethod());v.put("decision",row.getDecision());
            v.put("reason",row.getReason());v.put("rejectReason",row.getRejectReason());v.put("totalScore",row.getTotalScore());
            v.put("skillScore",row.getSkillScore());v.put("distanceScore",row.getDistanceScore());v.put("loadScore",row.getLoadScore());v.put("ratingScore",row.getRatingScore());
            v.put("createTime",row.getCreateTime());v.put("responseTime",row.getResponseTime());return (Map<String,Object>)v;}).toList();
    }
}

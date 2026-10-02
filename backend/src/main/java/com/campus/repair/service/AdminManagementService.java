package com.campus.repair.service;

import com.campus.repair.utils.BusinessTime;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.repair.common.*;
import com.campus.repair.dto.*;
import com.campus.repair.entity.*;
import com.campus.repair.mapper.*;
import com.campus.repair.security.UserRole;
import com.campus.repair.vo.*;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@lombok.RequiredArgsConstructor
public class AdminManagementService {
    private final UserMapper users;
    private final StudentMapper students;
    private final WorkerMapper workers;
    private final BuildingMapper buildings;
    private final RepairTypeMapper types;
    private final RepairOrderMapper orders;
    private final PasswordEncoder passwords;
    private final OrderAccessService access;
    private final Clock clock;
    private LocalDateTime now(){return BusinessTime.now(clock);}
    private void requireAdmin(UserVO actor){access.requireAdmin(actor);}
    private static String text(String value){return value==null?null:value.strip();}
    private static void required(String value){if(value==null||value.isBlank())throw new BusinessException(ErrorCode.BAD_REQUEST);}
    private void buildingExists(Long id){if(id!=null&&buildings.selectById(id)==null)throw new BusinessException(ErrorCode.INVALID_REFERENCE);}
    private static void coordinates(java.math.BigDecimal longitude,java.math.BigDecimal latitude){
        if((longitude==null)!=(latitude==null))throw new BusinessException(ErrorCode.BAD_REQUEST);
        if(longitude!=null&&(longitude.compareTo(java.math.BigDecimal.valueOf(-180))<0||longitude.compareTo(java.math.BigDecimal.valueOf(180))>0
                ||latitude.compareTo(java.math.BigDecimal.valueOf(-90))<0||latitude.compareTo(java.math.BigDecimal.valueOf(90))>0))
            throw new BusinessException(ErrorCode.BAD_REQUEST);
    }

    @Transactional(readOnly=true)
    public PageResult<AdminAccountVO> accounts(UserVO actor,UserRole role,long page,long size){
        requireAdmin(actor);
        var result=users.selectPage(new Page<>(page,size),new LambdaQueryWrapper<UserEntity>()
                .eq(role!=null,UserEntity::getRole,role).orderByAsc(UserEntity::getId));
        var ids=result.getRecords().stream().map(UserEntity::getId).toList();
        var studentRows=ids.isEmpty()?List.<StudentEntity>of():students.selectList(new LambdaQueryWrapper<StudentEntity>().in(StudentEntity::getUserId,ids));
        var workerRows=ids.isEmpty()?List.<WorkerEntity>of():workers.selectList(new LambdaQueryWrapper<WorkerEntity>().in(WorkerEntity::getUserId,ids));
        var studentMap=studentRows.stream().collect(Collectors.toMap(StudentEntity::getUserId,row->row));
        var workerMap=workerRows.stream().collect(Collectors.toMap(WorkerEntity::getUserId,row->row));
        var rows=result.getRecords().stream().map(user->{var student=studentMap.get(user.getId());var worker=workerMap.get(user.getId());
            return new AdminAccountVO(user.getId(),user.getUsername(),user.getRealName(),user.getPhone(),user.getRole(),user.getStatus(),
                    student==null?null:student.getStudentNo(),student==null?null:student.getCollege(),student==null?null:student.getClassName(),
                    student==null?null:student.getBuildingId(),student==null?null:student.getRoomNo(),
                    worker==null?null:worker.getId(),worker==null?null:worker.getSkillType(),worker==null?null:worker.getStatus(),
                    worker==null?null:worker.getLongitude(),worker==null?null:worker.getLatitude());}).toList();
        return new PageResult<>(rows,result.getTotal(),result.getCurrent(),result.getSize());
    }

    @Transactional
    public long create(UserVO actor,AccountCreateRequest input){
        requireAdmin(actor);
        if(input.role()!=UserRole.STUDENT&&input.role()!=UserRole.WORKER)throw new BusinessException(ErrorCode.BAD_REQUEST);
        if(input.password().getBytes(StandardCharsets.UTF_8).length>72)throw new BusinessException(ErrorCode.BAD_REQUEST);
        if(input.role()==UserRole.STUDENT){required(input.studentNo());required(input.college());required(input.className());buildingExists(input.buildingId());}
        else {required(input.skillType());coordinates(input.longitude(),input.latitude());}
        var user=new UserEntity();user.setUsername(input.username().strip());user.setPassword(passwords.encode(input.password()));
        user.setRealName(input.realName().strip());user.setPhone(text(input.phone()));user.setRole(input.role());
        user.setStatus(1);user.setTokenVersion(0L);user.setCreateTime(now());user.setUpdateTime(user.getCreateTime());
        try {
            users.insert(user);
            if(input.role()==UserRole.STUDENT){var student=new StudentEntity();student.setUserId(user.getId());
                student.setStudentNo(input.studentNo().strip());student.setCollege(input.college().strip());student.setClassName(input.className().strip());
                student.setBuildingId(input.buildingId());student.setRoomNo(text(input.roomNo()));students.insert(student);
            }else{var worker=new WorkerEntity();worker.setUserId(user.getId());worker.setSkillType(input.skillType().strip());
                worker.setScore(java.math.BigDecimal.ZERO);worker.setTaskCount(0);worker.setStatus(1);
                worker.setLongitude(input.longitude());worker.setLatitude(input.latitude());workers.insert(worker);}
        }catch(DataIntegrityViolationException duplicate){throw new BusinessException(ErrorCode.CONFLICT);}
        return user.getId();
    }

    @Transactional(isolation=Isolation.READ_COMMITTED)
    public void update(UserVO actor,long id,AccountUpdateRequest input){
        requireAdmin(actor);var user=users.lockById(id);if(user==null)throw new BusinessException(ErrorCode.NOT_FOUND);
        user.setRealName(input.realName().strip());user.setPhone(text(input.phone()));user.setUpdateTime(now());
        try {
            if(user.getRole()==UserRole.STUDENT){required(input.studentNo());required(input.college());required(input.className());buildingExists(input.buildingId());
                var student=students.selectOne(new LambdaQueryWrapper<StudentEntity>().eq(StudentEntity::getUserId,id));
                if(student==null)throw new BusinessException(ErrorCode.PROFILE_REQUIRED);
                student.setStudentNo(input.studentNo().strip());student.setCollege(input.college().strip());student.setClassName(input.className().strip());
                student.setBuildingId(input.buildingId());student.setRoomNo(text(input.roomNo()));students.updateById(student);
            }else if(user.getRole()==UserRole.WORKER){required(input.skillType());coordinates(input.longitude(),input.latitude());
                var worker=workers.selectOne(new LambdaQueryWrapper<WorkerEntity>().eq(WorkerEntity::getUserId,id));
                if(worker==null)throw new BusinessException(ErrorCode.PROFILE_REQUIRED);
                workers.update(null,new LambdaUpdateWrapper<WorkerEntity>().eq(WorkerEntity::getId,worker.getId())
                        .set(WorkerEntity::getSkillType,input.skillType().strip())
                        .set(WorkerEntity::getLongitude,input.longitude()).set(WorkerEntity::getLatitude,input.latitude()));
            }
            users.updateById(user);
        }catch(DataIntegrityViolationException duplicate){throw new BusinessException(ErrorCode.CONFLICT);}
    }

    @Transactional(isolation=Isolation.READ_COMMITTED)
    public void setStatus(UserVO actor,long id,int status){
        requireAdmin(actor);
        var target=users.selectById(id);if(target==null)throw new BusinessException(ErrorCode.NOT_FOUND);
        if(target.getRole()==UserRole.ADMIN)users.lockActiveAdmins();
        var user=users.lockById(id);
        if(user.getStatus()==status)return;
        if(status==0&&user.getRole()==UserRole.ADMIN&&users.lockActiveAdmins().size()<=1)
            throw new BusinessException(ErrorCode.LAST_ADMIN);
        if(status==0&&user.getRole()==UserRole.WORKER){
            var worker=workers.selectOne(new LambdaQueryWrapper<WorkerEntity>().eq(WorkerEntity::getUserId,id));
            if(worker!=null){worker=workers.lockById(worker.getId());
                long workerId=worker.getId();
                if(orders.activeLoads().stream().anyMatch(row->row.getWorkerId().equals(workerId)))
                    throw new BusinessException(ErrorCode.WORKER_HAS_TASKS);}
        }
        user.setStatus(status);user.setTokenVersion(user.getTokenVersion()+1);user.setUpdateTime(now());users.updateById(user);
    }

    @Transactional(isolation=Isolation.READ_COMMITTED)
    public void setWorkerStatus(UserVO actor,long workerId,int status){
        requireAdmin(actor);var worker=workers.lockById(workerId);if(worker==null)throw new BusinessException(ErrorCode.NOT_FOUND);
        if(status==0&&orders.activeLoads().stream().anyMatch(row->row.getWorkerId().equals(workerId)))
            throw new BusinessException(ErrorCode.WORKER_HAS_TASKS);
        worker.setStatus(status);workers.updateById(worker);
    }

    @Transactional
    public long saveBuilding(UserVO actor,Long id,BuildingRequest input){
        requireAdmin(actor);coordinates(input.longitude(),input.latitude());
        var row=id==null?new BuildingEntity():buildings.selectById(id);
        if(row==null)throw new BusinessException(ErrorCode.NOT_FOUND);
        row.setName(input.name().strip());row.setType(input.type().strip());row.setLongitude(input.longitude());row.setLatitude(input.latitude());
        try {if(id==null)buildings.insert(row);else buildings.updateById(row);}
        catch(DataIntegrityViolationException duplicate){throw new BusinessException(ErrorCode.CONFLICT);}
        return row.getId();
    }
    @Transactional
    public long saveType(UserVO actor,Long id,RepairTypeRequest input){
        requireAdmin(actor);var row=id==null?new RepairTypeEntity():types.selectById(id);
        if(row==null)throw new BusinessException(ErrorCode.NOT_FOUND);
        row.setName(input.name().strip());row.setDescription(input.description().strip());
        try {if(id==null)types.insert(row);else types.updateById(row);}
        catch(DataIntegrityViolationException duplicate){throw new BusinessException(ErrorCode.CONFLICT);}
        return row.getId();
    }
}

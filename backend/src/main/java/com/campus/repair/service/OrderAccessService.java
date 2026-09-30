package com.campus.repair.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.repair.common.BusinessException;
import com.campus.repair.common.ErrorCode;
import com.campus.repair.entity.*;
import com.campus.repair.mapper.*;
import com.campus.repair.security.UserRole;
import com.campus.repair.vo.UserVO;
import org.springframework.stereotype.Service;

@Service
@lombok.RequiredArgsConstructor
public class OrderAccessService {
    private final StudentMapper students;
    private final WorkerMapper workers;
    private final UserMapper users;

    public StudentEntity student(UserVO user) {
        if (user.role() != UserRole.STUDENT) throw new BusinessException(ErrorCode.FORBIDDEN);
        var result = students.selectOne(new LambdaQueryWrapper<StudentEntity>().eq(StudentEntity::getUserId, user.id()));
        if (result == null) throw new BusinessException(ErrorCode.PROFILE_REQUIRED);
        return result;
    }

    public WorkerEntity worker(UserVO user) {
        if (user.role() != UserRole.WORKER) throw new BusinessException(ErrorCode.FORBIDDEN);
        var result = workers.selectOne(new LambdaQueryWrapper<WorkerEntity>().eq(WorkerEntity::getUserId, user.id()));
        if (result == null) throw new BusinessException(ErrorCode.PROFILE_REQUIRED);
        return result;
    }

    public void requireAdmin(UserVO user) {
        if (user.role() != UserRole.ADMIN) throw new BusinessException(ErrorCode.FORBIDDEN);
    }

    public void requireView(UserVO user, RepairOrderEntity order) {
        if (order == null) throw new BusinessException(ErrorCode.NOT_FOUND);
        boolean allowed = switch (user.role()) {
            case ADMIN -> true;
            case STUDENT -> order.getStudentId().equals(student(user).getId());
            case WORKER -> order.getWorkerId() != null && order.getWorkerId().equals(worker(user).getId());
        };
        if (!allowed) throw new BusinessException(ErrorCode.NOT_FOUND);
    }

    public void requireStudentOwner(UserVO user, RepairOrderEntity order) {
        student(user);
        requireView(user, order);
    }

    public WorkerEntity requireWorkerOwner(UserVO user, RepairOrderEntity order) {
        var worker = worker(user);
        requireView(user, order);
        requireAvailable(worker);
        return worker;
    }

    public void requireAvailable(WorkerEntity worker) {
        if (worker == null || worker.getStatus() != 1) throw new BusinessException(ErrorCode.WORKER_UNAVAILABLE);
        var account = users.selectById(worker.getUserId());
        if (account == null || account.getStatus() != 1 || account.getRole() != UserRole.WORKER)
            throw new BusinessException(ErrorCode.WORKER_UNAVAILABLE);
    }
}

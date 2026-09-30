package com.campus.repair.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.repair.entity.*;
import com.campus.repair.mapper.*;
import com.campus.repair.security.UserRole;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/** Shared read model for recommendations and static worker markers. */
@Service
@lombok.RequiredArgsConstructor
public class DispatchDataService {
    private final UserMapper users;
    private final WorkerMapper workers;
    private final RepairOrderMapper orders;

    public record Candidate(WorkerEntity worker, String name) {}
    public List<Candidate> available() {
        var accounts = users.selectList(new LambdaQueryWrapper<UserEntity>()
                .eq(UserEntity::getRole, UserRole.WORKER).eq(UserEntity::getStatus, 1));
        if (accounts.isEmpty()) return List.of();
        var names = accounts.stream().collect(Collectors.toMap(UserEntity::getId, UserEntity::getRealName));
        return workers.selectList(new LambdaQueryWrapper<WorkerEntity>()
                .in(WorkerEntity::getUserId, names.keySet()).eq(WorkerEntity::getStatus, 1).orderByAsc(WorkerEntity::getId))
                .stream().map(worker -> new Candidate(worker, names.get(worker.getUserId()))).toList();
    }
    public Map<Long, Long> activeLoads() {
        return orders.activeLoads().stream().collect(Collectors.toMap(
                com.campus.repair.vo.WorkerLoadVO::getWorkerId, com.campus.repair.vo.WorkerLoadVO::getActiveCount));
    }
    public String name(WorkerEntity worker) { return users.selectById(worker.getUserId()).getRealName(); }
}

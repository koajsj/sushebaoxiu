package com.campus.repair.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.repair.common.PageResult;
import com.campus.repair.entity.*;
import com.campus.repair.mapper.*;
import com.campus.repair.vo.*;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@lombok.RequiredArgsConstructor
@Transactional(readOnly=true)
public class MapService {
    private final BuildingMapper buildings;
    private final RepairTypeMapper types;
    private final RepairOrderMapper orders;
    private final OrderAccessService access;
    private final DispatchDataService data;

    public List<BuildingEntity> buildings(UserVO user) {
        access.requireAdmin(user);
        return buildings.selectList(new LambdaQueryWrapper<BuildingEntity>().orderByAsc(BuildingEntity::getId));
    }
    public PageResult<MapOrderVO> orders(UserVO user, String status) {
        access.requireAdmin(user);
        var query = new LambdaQueryWrapper<RepairOrderEntity>();
        var filter = OrderStatus.filter(status);
        if (filter != null) query.eq(RepairOrderEntity::getStatus, filter.name());
        query.orderByDesc(RepairOrderEntity::getCreateTime, RepairOrderEntity::getId);
        var request = new Page<RepairOrderEntity>(1,500);
        request.setMaxLimit(500L); // Keep the existing 100-row limit on ordinary list APIs.
        var page = orders.selectPage(request, query);
        var locations = buildings.selectList(null).stream().collect(Collectors.toMap(BuildingEntity::getId, row -> row));
        var names = types.selectList(null).stream().collect(Collectors.toMap(RepairTypeEntity::getId, RepairTypeEntity::getName));
        var result = page.getRecords().stream().map(order -> {
            var building = locations.get(order.getBuildingId());
            return new MapOrderVO(order.getId(), order.getTitle(), order.getStatus(),
                    (building == null ? "未知楼栋" : building.getName())+" · "+order.getRoomNo(), names.get(order.getTypeId()),
                    order.getWorkerId(), building == null ? null : building.getLongitude(),
                    building == null ? null : building.getLatitude(), order.getCreateTime());
        }).toList();
        return new PageResult<>(result, page.getTotal(),1,500);
    }
    public List<MapWorkerVO> workers(UserVO user) {
        access.requireAdmin(user);
        var loads = data.activeLoads();
        return data.available().stream().map(candidate -> {
            var worker = candidate.worker(); long active = loads.getOrDefault(worker.getId(),0L);
            return new MapWorkerVO(worker.getId(),candidate.name(),worker.getSkillType(),active == 0 ? "AVAILABLE" : "BUSY",
                    active,worker.getLongitude(),worker.getLatitude());
        }).toList();
    }
}

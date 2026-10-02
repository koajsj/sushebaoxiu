package com.campus.repair.service;

import com.campus.repair.utils.BusinessTime;
import com.campus.repair.algorithm.DispatchAlgorithm;
import com.campus.repair.common.*;
import com.campus.repair.dto.DispatchRequest;
import com.campus.repair.entity.*;
import com.campus.repair.mapper.*;
import com.campus.repair.vo.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@lombok.RequiredArgsConstructor
public class DispatchService {
    private final RepairOrderMapper orders;
    private final BuildingMapper buildings;
    private final RepairTypeMapper types;
    private final WorkerMapper workers;
    private final DispatchRecordMapper records;
    private final OrderAccessService access;
    private final DispatchDataService data;
    private final DispatchAlgorithm algorithm;
    private final Clock clock;
    private final OrderWorkflowService workflow;
    private final DispatchSnapshotService snapshots;

    private LocalDateTime now() {
        // DATETIME(0) rounds fractions; truncate explicitly so a fresh snapshot is never in the future.
        return BusinessTime.now(clock);
    }
    private RepairOrderEntity eligibleOrder(long id) {
        return requireEligible(orders.lockById(id));
    }
    private RepairOrderEntity eligibleOrderRead(long id) {
        return requireEligible(orders.selectById(id));
    }
    private RepairOrderEntity requireEligible(RepairOrderEntity order) {
        if (order == null) throw new BusinessException(ErrorCode.NOT_FOUND);
        OrderStatus.require(order.getStatus(), OrderStatus.WAIT_ASSIGN);
        if (order.getWorkerId() != null) throw new BusinessException(ErrorCode.CONFLICT);
        return order;
    }
    private static Double coordinate(BigDecimal value) { return value == null ? null : value.doubleValue(); }
    private DispatchAlgorithm.Scores score(RepairOrderEntity order, WorkerEntity worker, long load) {
        var building = buildings.selectById(order.getBuildingId());
        var type = types.selectById(order.getTypeId());
        if (building == null || type == null) throw new BusinessException(ErrorCode.INVALID_REFERENCE);
        return score(type, building, worker, load);
    }
    private DispatchAlgorithm.Scores score(RepairTypeEntity type, BuildingEntity building, WorkerEntity worker, long load) {
        return algorithm.score(new DispatchAlgorithm.Input(type.getName(), type.getDescription(), worker.getSkillType(),
                coordinate(building.getLongitude()), coordinate(building.getLatitude()),
                coordinate(worker.getLongitude()), coordinate(worker.getLatitude()), load, worker.getScore().doubleValue()));
    }
    private DispatchRecordEntity recommendation(long orderId, long workerId, DispatchAlgorithm.Scores scores, String batch, boolean confirmed, int round) {
        var row = new DispatchRecordEntity();
        row.setOrderId(orderId); row.setWorkerId(workerId); row.setSkillScore(scores.skillScore());
        row.setDistanceScore(scores.distanceScore()); row.setLoadScore(scores.loadScore()); row.setRatingScore(scores.ratingScore());
        row.setTotalScore(scores.totalScore()); row.setReason(scores.reason()); row.setRecommendationBatch(batch);
        row.setRoundNo(round);row.setConfirmed(confirmed); row.setCreateTime(now()); row.setMethod("SMART");row.setDecision(confirmed?"ASSIGNED":"RECOMMENDED");
        return row;
    }
    private WorkerRecommendationVO view(DispatchRecordEntity row, WorkerEntity worker, String name, long load, Double distance) {
        return new WorkerRecommendationVO(row.getId(), worker.getId(), name, worker.getSkillType(), load,
                worker.getTaskCount(), worker.getScore(), row.getSkillScore(), row.getDistanceScore(), row.getLoadScore(),
                row.getRatingScore(), row.getTotalScore(), distance, row.getReason(),
                row.getCreateTime().plusMinutes(10).atZone(ZoneId.of("Asia/Shanghai")).toInstant());
    }

    private List<WorkerRecommendationVO> views(RepairOrderEntity order,List<DispatchRecordEntity> rows) {
        if(rows.isEmpty())return List.of();
        var candidates=new HashMap<Long,DispatchDataService.Candidate>();
        data.available().forEach(candidate->candidates.put(candidate.worker().getId(),candidate));
        var loads=data.activeLoads();
        var building=buildings.selectById(order.getBuildingId());
        var type=types.selectById(order.getTypeId());
        if(building==null||type==null)throw new BusinessException(ErrorCode.INVALID_REFERENCE);
        return rows.stream().filter(row->candidates.containsKey(row.getWorkerId())).map(row->{
            var candidate=candidates.get(row.getWorkerId());var worker=candidate.worker();
            long load=loads.getOrDefault(worker.getId(),0L);
            var fresh=score(type,building,worker,load);
            return view(row,worker,candidate.name(),load,fresh.distanceKm());
        }).sorted(Comparator.comparing(WorkerRecommendationVO::totalScore).reversed()
                .thenComparing(WorkerRecommendationVO::distanceKm,Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(WorkerRecommendationVO::workerId)).toList();
    }

    @Transactional(readOnly=true)
    public List<WorkerRecommendationVO> recommend(UserVO user, long orderId) {
        access.requireAdmin(user);
        var order=eligibleOrderRead(orderId);
        return views(order,snapshots.current(orderId,order.getDispatchRound()+1));
    }

    public List<WorkerRecommendationVO> generate(UserVO user,long orderId,boolean refresh) {
        access.requireAdmin(user);
        var order=eligibleOrderRead(orderId);
        var cached=snapshots.current(orderId,order.getDispatchRound()+1);
        if(!refresh&&!cached.isEmpty())return views(order,cached);
        var loads = data.activeLoads();
        String batch = UUID.randomUUID().toString();
        var calculated = new ArrayList<DispatchRecordEntity>();
        // Load common references once per batch, rather than per candidate.
        var building = buildings.selectById(order.getBuildingId());
        var type = types.selectById(order.getTypeId());
        if (building == null || type == null) throw new BusinessException(ErrorCode.INVALID_REFERENCE);
        for (var candidate : data.available()) {
            var worker = candidate.worker();
            long load = loads.getOrDefault(worker.getId(), 0L);
            var scores = score(type,building,worker,load);
            calculated.add(recommendation(orderId,worker.getId(),scores,batch,false,order.getDispatchRound()+1));
        }
        if(calculated.isEmpty())return List.of();
        return views(order,snapshots.persist(orderId,order.getDispatchRound()+1,calculated,refresh));
    }

    @Transactional(isolation=Isolation.READ_COMMITTED)
    public WorkerRecommendationVO confirm(UserVO user, DispatchRequest input) {
        access.requireAdmin(user);
        var order = eligibleOrder(input.orderId());
        var snapshot = records.selectById(input.recommendationId());
        if (snapshot == null || !snapshot.getOrderId().equals(input.orderId()) || !snapshot.getWorkerId().equals(input.workerId())
                || !Objects.equals(snapshot.getRoundNo(),order.getDispatchRound()+1))
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        // Same lock order as manual assignment: order, then worker. Serializes active load changes.
        var worker = workers.lockById(input.workerId());
        access.requireAvailable(worker);
        // A lock wait can cross the expiry boundary; read the clock after acquiring both locks.
        var currentTime = now();
        if (Boolean.TRUE.equals(snapshot.getConfirmed()) || snapshot.getCreateTime().isBefore(currentTime.minusMinutes(10))
                || snapshot.getCreateTime().isAfter(currentTime)) throw new BusinessException(ErrorCode.CONFLICT);
        long load = data.activeLoads().getOrDefault(worker.getId(), 0L);
        var fresh = score(order, worker, load);
        if (!snapshot.getReason().equals(fresh.reason()) || snapshot.getTotalScore().compareTo(fresh.totalScore()) != 0
                || snapshot.getSkillScore().compareTo(fresh.skillScore()) != 0
                || snapshot.getDistanceScore().compareTo(fresh.distanceScore()) != 0
                || snapshot.getLoadScore().compareTo(fresh.loadScore()) != 0
                || snapshot.getRatingScore().compareTo(fresh.ratingScore()) != 0)
            throw new BusinessException(ErrorCode.RECOMMENDATION_STALE);
        var confirmed = recommendation(order.getId(), worker.getId(), fresh, snapshot.getRecommendationBatch(), true, order.getDispatchRound()+1);
        records.insert(confirmed);
        order.setWorkerId(worker.getId()); order.setStatus(OrderStatus.ASSIGNED.name()); order.setUpdateTime(currentTime);
        workflow.assigned(order,user,confirmed);
        return view(confirmed, worker, data.name(worker), load, fresh.distanceKm());
    }
}

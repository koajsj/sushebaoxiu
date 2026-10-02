package com.campus.repair.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.campus.repair.algorithm.DispatchAlgorithm;
import com.campus.repair.common.*;
import com.campus.repair.dto.DispatchRequest;
import com.campus.repair.entity.*;
import com.campus.repair.mapper.*;
import com.campus.repair.security.UserRole;
import com.campus.repair.vo.UserVO;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class DispatchServiceTest {
    private final RepairOrderMapper orders=mock(RepairOrderMapper.class);
    private final BuildingMapper buildings=mock(BuildingMapper.class);
    private final RepairTypeMapper types=mock(RepairTypeMapper.class);
    private final WorkerMapper workers=mock(WorkerMapper.class);
    private final DispatchRecordMapper records=mock(DispatchRecordMapper.class);
    private final OrderAccessService access=mock(OrderAccessService.class);
    private final DispatchDataService data=mock(DispatchDataService.class);
    private final DispatchSnapshotService snapshots=mock(DispatchSnapshotService.class);
    private final OrderWorkflowService workflow=mock(OrderWorkflowService.class);
    private final Clock clock=mock(Clock.class);
    private final AtomicReference<Instant> time=new AtomicReference<>(Instant.parse("2026-09-30T00:00:00Z"));
    private final DispatchAlgorithm algorithm=new DispatchAlgorithm();
    private final DispatchService service=new DispatchService(orders,buildings,types,workers,records,access,data,algorithm,clock,workflow,snapshots);
    private final UserVO admin=new UserVO(3,"admin","管理员",null,UserRole.ADMIN);
    private DispatchRecordEntity setup() {
        when(clock.instant()).thenAnswer(call->time.get());
        var order=new RepairOrderEntity();order.setId(1L);order.setStatus("WAIT_ASSIGN");order.setBuildingId(1L);order.setTypeId(1L);
        when(orders.lockById(1)).thenReturn(order);when(orders.selectById(1L)).thenReturn(order);
        when(snapshots.current(1,1)).thenReturn(List.of());
        var building=new BuildingEntity();building.setLongitude(new BigDecimal("116.3"));building.setLatitude(new BigDecimal("39.9"));
        when(buildings.selectById(1L)).thenReturn(building);
        var type=new RepairTypeEntity();type.setName("照明与电路");type.setDescription("灯具开关");when(types.selectById(1L)).thenReturn(type);
        var worker=new WorkerEntity();worker.setId(1L);worker.setUserId(2L);worker.setSkillType("电工");worker.setScore(new BigDecimal("5"));worker.setTaskCount(0);worker.setLongitude(building.getLongitude());worker.setLatitude(building.getLatitude());
        when(workers.lockById(1)).thenReturn(worker);when(data.activeLoads()).thenReturn(Map.of());when(data.name(worker)).thenReturn("维修员");
        var scores=algorithm.score(new DispatchAlgorithm.Input(type.getName(),type.getDescription(),worker.getSkillType(),116.3,39.9,116.3,39.9,0,5));
        var snapshot=new DispatchRecordEntity();snapshot.setRoundNo(1);snapshot.setId(5L);snapshot.setOrderId(1L);snapshot.setWorkerId(1L);snapshot.setConfirmed(false);snapshot.setRecommendationBatch("test-batch");snapshot.setCreateTime(LocalDateTime.ofInstant(time.get(),ZoneId.of("Asia/Shanghai")));
        snapshot.setReason(scores.reason());snapshot.setTotalScore(scores.totalScore());snapshot.setSkillScore(scores.skillScore());snapshot.setDistanceScore(scores.distanceScore());snapshot.setLoadScore(scores.loadScore());snapshot.setRatingScore(scores.ratingScore());
        when(records.selectById(5L)).thenReturn(snapshot);
        return snapshot;
    }
    @Test void expiryIsRecheckedAfterWorkerLockWait() {
        var snapshot=setup();snapshot.setCreateTime(snapshot.getCreateTime().minusMinutes(10).plusSeconds(1));
        var worker=workers.lockById(1);when(workers.lockById(1)).thenAnswer(call->{time.updateAndGet(t->t.plusSeconds(2));return worker;});
        var error=assertThrows(BusinessException.class,()->service.confirm(admin,new DispatchRequest(1L,1L,5L)));
        assertEquals(ErrorCode.CONFLICT,error.getErrorCode());verify(records,never()).insert(any(DispatchRecordEntity.class));
    }
    @Test void everyFactorIsValidatedEvenIfTotalAndReasonAreUnchanged() {
        var snapshot=setup();snapshot.setSkillScore(new BigDecimal("70.00"));
        var error=assertThrows(BusinessException.class,()->service.confirm(admin,new DispatchRequest(1L,1L,5L)));
        assertEquals(ErrorCode.RECOMMENDATION_STALE,error.getErrorCode());verify(orders,never()).updateById(any(RepairOrderEntity.class));
    }
    @Test void confirmationPreservesRecommendationFieldsAndWorkflow() {
        var snapshot=setup();
        var result=service.confirm(admin,new DispatchRequest(1L,1L,5L));
        var saved=org.mockito.ArgumentCaptor.forClass(DispatchRecordEntity.class);
        verify(records).insert(saved.capture());
        var row=saved.getValue();
        assertAll(
                ()->assertEquals(snapshot.getOrderId(),row.getOrderId()),
                ()->assertEquals(snapshot.getWorkerId(),row.getWorkerId()),
                ()->assertEquals(snapshot.getRecommendationBatch(),row.getRecommendationBatch()),
                ()->assertEquals(snapshot.getRoundNo(),row.getRoundNo()),
                ()->assertEquals(snapshot.getSkillScore(),row.getSkillScore()),
                ()->assertEquals(snapshot.getDistanceScore(),row.getDistanceScore()),
                ()->assertEquals(snapshot.getLoadScore(),row.getLoadScore()),
                ()->assertEquals(snapshot.getRatingScore(),row.getRatingScore()),
                ()->assertEquals(snapshot.getTotalScore(),row.getTotalScore()),
                ()->assertEquals(snapshot.getReason(),row.getReason()),
                ()->assertEquals(snapshot.getCreateTime(),row.getCreateTime()),
                ()->assertTrue(row.getConfirmed()),
                ()->assertEquals("SMART",row.getMethod()),
                ()->assertEquals("ASSIGNED",row.getDecision()),
                ()->assertEquals(row.getTotalScore(),result.totalScore()));
        verify(workflow).assigned(argThat(order->"ASSIGNED".equals(order.getStatus())&&Long.valueOf(1).equals(order.getWorkerId())),eq(admin),same(row));
    }
    @Test void storedTimestampCannotRoundIntoTheFuture() {
        setup();time.set(Instant.parse("2026-09-30T00:00:00.600Z"));
        var worker=workers.lockById(1);
        when(data.available()).thenReturn(java.util.List.of(new DispatchDataService.Candidate(worker,"维修员")));
        var writtenTime=new AtomicReference<LocalDateTime>();
        when(snapshots.persist(eq(1L),eq(1),anyList(),eq(true))).thenAnswer(call->{
            List<DispatchRecordEntity> rows=call.getArgument(2);
            writtenTime.set(rows.get(0).getCreateTime());return rows;
        });
        service.generate(admin,1,true);
        verify(snapshots).persist(eq(1L),eq(1),anyList(),eq(true));
        var written=writtenTime.get();
        var databaseTime=written.withNano(0).plusSeconds(written.getNano()>=500_000_000?1:0);
        var currentTime=LocalDateTime.ofInstant(time.get(),ZoneId.of("Asia/Shanghai"));
        assertFalse(databaseTime.isAfter(currentTime),"MySQL DATETIME(0) must not persist a future snapshot");
    }
}

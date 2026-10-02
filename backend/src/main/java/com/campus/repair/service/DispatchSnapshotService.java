package com.campus.repair.service;

import com.campus.repair.utils.BusinessTime;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.repair.common.BusinessException;
import com.campus.repair.common.ErrorCode;
import com.campus.repair.entity.DispatchRecordEntity;
import com.campus.repair.mapper.DispatchRecordMapper;
import com.campus.repair.mapper.RepairOrderMapper;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@lombok.RequiredArgsConstructor
public class DispatchSnapshotService {
    private final RepairOrderMapper orders;
    private final DispatchRecordMapper records;
    private final Clock clock;
    private LocalDateTime now(){return BusinessTime.now(clock);}

    public List<DispatchRecordEntity> current(long orderId,int nextRound){
        var latest=records.selectList(new LambdaQueryWrapper<DispatchRecordEntity>()
                .eq(DispatchRecordEntity::getOrderId,orderId).eq(DispatchRecordEntity::getRoundNo,nextRound)
                .eq(DispatchRecordEntity::getConfirmed,false).ge(DispatchRecordEntity::getCreateTime,now().minusMinutes(10))
                .le(DispatchRecordEntity::getCreateTime,now()).orderByDesc(DispatchRecordEntity::getId).last("LIMIT 1"));
        if(latest.isEmpty())return List.of();
        return records.selectList(new LambdaQueryWrapper<DispatchRecordEntity>()
                .eq(DispatchRecordEntity::getOrderId,orderId)
                .eq(DispatchRecordEntity::getRecommendationBatch,latest.get(0).getRecommendationBatch())
                .eq(DispatchRecordEntity::getConfirmed,false).orderByAsc(DispatchRecordEntity::getId));
    }

    /** Computation happens before this short transaction; the order lock only serializes batch insertion. */
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public List<DispatchRecordEntity> persist(long orderId,int nextRound,List<DispatchRecordEntity> calculated,boolean refresh){
        var order=orders.lockById(orderId);
        if(order==null)throw new BusinessException(ErrorCode.NOT_FOUND);
        if(!"WAIT_ASSIGN".equals(order.getStatus())||order.getWorkerId()!=null||order.getDispatchRound()+1!=nextRound)
            throw new BusinessException(ErrorCode.CONFLICT);
        var existing=current(orderId,nextRound);
        if(!refresh&&!existing.isEmpty())return existing;
        for(var row:calculated)records.insert(row);
        return calculated;
    }
}

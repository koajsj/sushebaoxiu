package com.campus.repair.vo;

import com.campus.repair.entity.*;
import java.util.List;

public record OrderDetailVO(OrderVO order, List<RepairRecordEntity> records,
        EvaluationEntity evaluation, List<OrderEventEntity> timeline, List<java.util.Map<String,Object>> dispatchHistory) {}

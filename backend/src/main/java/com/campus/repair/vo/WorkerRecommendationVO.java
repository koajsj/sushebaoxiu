package com.campus.repair.vo;

import java.math.BigDecimal;
public record WorkerRecommendationVO(Long recommendationId, Long workerId, String workerName, String skillType,
        long activeTaskCount, int completedTaskCount, BigDecimal rating,
        BigDecimal skillScore, BigDecimal distanceScore, BigDecimal loadScore, BigDecimal ratingScore,
        BigDecimal totalScore, Double distanceKm, String reason) {}

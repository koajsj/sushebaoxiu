package com.campus.repair.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
public record DispatchRequest(@NotNull @Positive Long orderId, @NotNull @Positive Long workerId,
        @NotNull @Positive Long recommendationId) {}

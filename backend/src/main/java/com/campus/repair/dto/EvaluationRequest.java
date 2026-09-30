package com.campus.repair.dto;

import jakarta.validation.constraints.*;

public record EvaluationRequest(@NotNull @Positive Long orderId,
        @NotNull @Min(1) @Max(5) Integer score,
        @Size(max=1000) String content) {}

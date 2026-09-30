package com.campus.repair.dto;

import jakarta.validation.constraints.*;

public record RepairRecordRequest(@NotNull @Positive Long orderId,
        @NotBlank @Size(max=2000) String content,
        @Size(max=100) String imageUrl) {}

package com.campus.repair.dto;

import jakarta.validation.constraints.*;

public record RepairRecordRequest(@NotNull @Positive Long orderId,
        @NotBlank @Size(max=2000) String content,
        @Size(max=100) String imageUrl,
        @NotBlank @Pattern(regexp="[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}") String requestKey) {}

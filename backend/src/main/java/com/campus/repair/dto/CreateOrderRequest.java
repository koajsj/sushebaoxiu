package com.campus.repair.dto;

import jakarta.validation.constraints.*;

public record CreateOrderRequest(@NotNull @Positive Long typeId,
        @NotBlank @Size(max=120) String title,
        @NotBlank @Size(max=2000) String description,
        @Size(max=100) String imageUrl,
        @NotNull @Positive Long buildingId,
        @NotBlank @Size(max=30) String roomNo,
        @NotBlank @Pattern(regexp="LOW|NORMAL|HIGH") String priority,
        @Pattern(regexp="[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}") String requestKey) {}

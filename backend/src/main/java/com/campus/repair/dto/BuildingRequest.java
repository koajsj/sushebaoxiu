package com.campus.repair.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record BuildingRequest(@NotBlank @Size(max=80) String name,@NotBlank @Size(max=30) String type,
        @DecimalMin("-180") @DecimalMax("180") BigDecimal longitude,
        @DecimalMin("-90") @DecimalMax("90") BigDecimal latitude) {}

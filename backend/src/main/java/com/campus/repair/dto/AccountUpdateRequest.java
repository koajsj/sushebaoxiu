package com.campus.repair.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record AccountUpdateRequest(@NotBlank @Size(max=64) String realName,@Size(max=20) String phone,
        @Size(max=40) String studentNo,@Size(max=80) String college,@Size(max=80) String className,
        Long buildingId,@Size(max=30) String roomNo,@Size(max=80) String skillType,
        BigDecimal longitude,BigDecimal latitude) {}

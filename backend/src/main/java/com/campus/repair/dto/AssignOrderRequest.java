package com.campus.repair.dto;

import jakarta.validation.constraints.*;

public record AssignOrderRequest(@NotNull @Positive Long workerId) {}

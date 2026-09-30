package com.campus.repair.dto;
import jakarta.validation.constraints.*;
public record ReasonRequest(@NotBlank @Size(max=500) String reason) {}

package com.campus.repair.dto;

import jakarta.validation.constraints.*;

public record RepairTypeRequest(@NotBlank @Size(max=60) String name,@NotBlank @Size(max=300) String description) {}

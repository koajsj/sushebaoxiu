package com.campus.repair.dto;
import jakarta.validation.constraints.*;
public record AppointmentResponse(@NotNull @Min(1) Integer version,@NotNull Boolean accepted,@Size(max=500) String reason) {}

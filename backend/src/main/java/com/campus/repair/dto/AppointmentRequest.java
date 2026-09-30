package com.campus.repair.dto;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
public record AppointmentRequest(@NotNull LocalDateTime start,@NotNull LocalDateTime end,@NotNull @Min(0) Integer version) {}

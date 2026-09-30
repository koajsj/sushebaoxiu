package com.campus.repair.dto;
import jakarta.validation.constraints.*;
public record ReworkRequest(@NotBlank @Pattern(regexp="ORIGINAL|REDISPATCH") String mode) {}

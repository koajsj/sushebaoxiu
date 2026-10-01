package com.campus.repair.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;

public record RecallRequest(@NotBlank @Size(max=500) String reason, boolean confirmProcessing,
        @NotBlank String expectedPhase,@NotNull @Min(0) Integer expectedDispatchRound) {}

package com.example.tripaudit.api.dto;

import com.example.tripaudit.domain.StopType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public record StopRequest(
        @NotNull @Min(1) Integer sequence,
        @NotNull StopType type,
        @NotBlank String employeeCode,
        @NotNull Instant plannedTime,
        String otp
) {}

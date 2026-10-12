package com.example.tripaudit.api.dto;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public record SosRequest(
        @NotNull Long employeeId,
        Instant pressedAt
) {}

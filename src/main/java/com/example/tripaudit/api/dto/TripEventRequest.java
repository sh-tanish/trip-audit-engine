package com.example.tripaudit.api.dto;

import com.example.tripaudit.domain.TripEventType;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public record TripEventRequest(
        @NotNull TripEventType type,
        Long stopId,
        String otp,
        @NotNull Instant occurredAt
) {}

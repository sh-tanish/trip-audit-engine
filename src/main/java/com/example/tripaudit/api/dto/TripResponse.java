package com.example.tripaudit.api.dto;

import com.example.tripaudit.domain.TripStatus;
import java.time.Instant;
import java.util.List;

/**
 * Public representation of a Trip. Contains generated stop IDs and NEVER any OTP fields.
 */
public record TripResponse(
        Long id,
        Long driverId,
        Long vehicleId,
        boolean escortAssigned,
        TripStatus status,
        Instant createdAt,
        Instant completedAt,
        List<StopResponse> stops
) {}

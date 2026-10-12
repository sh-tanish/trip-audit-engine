package com.example.tripaudit.api.dto;

import com.example.tripaudit.domain.StopStatus;

public record TripEventResponse(
        Long eventId,
        StopStatus stopStatus
) {}

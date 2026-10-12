package com.example.tripaudit.api.dto;

import com.example.tripaudit.domain.AlertChannel;
import com.example.tripaudit.domain.DeliveryStatus;
import java.time.Instant;

public record AlertDeliveryResponse(
        Long id,
        AlertChannel channel,
        DeliveryStatus status,
        int attempts,
        String lastError,
        Instant updatedAt
) {}

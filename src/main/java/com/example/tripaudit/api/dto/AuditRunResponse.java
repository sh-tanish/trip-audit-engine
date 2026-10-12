package com.example.tripaudit.api.dto;

import com.example.tripaudit.domain.AuditRunKind;
import java.time.Instant;

public record AuditRunResponse(
        String id,
        Long tripId,
        AuditRunKind kind,
        String triggeredBy,
        Instant startedAt,
        Instant finishedAt,
        int newViolations
) {}

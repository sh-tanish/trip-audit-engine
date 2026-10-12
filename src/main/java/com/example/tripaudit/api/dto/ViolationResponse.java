package com.example.tripaudit.api.dto;

import com.example.tripaudit.domain.Severity;
import com.example.tripaudit.domain.ViolationType;
import java.time.Instant;
import java.util.List;

public record ViolationResponse(
        Long id,
        String dedupKey,
        Long tripId,
        ViolationType type,
        Severity severity,
        String ruleName,
        String subjectKey,
        Long stopId,
        Long employeeId,
        String evidence,
        Instant detectedAt,
        String auditRunId,
        List<AlertDeliveryResponse> deliveries
) {}

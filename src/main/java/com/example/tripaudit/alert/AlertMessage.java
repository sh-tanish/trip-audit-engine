package com.example.tripaudit.alert;

import com.example.tripaudit.domain.Severity;
import com.example.tripaudit.domain.ViolationType;

/**
 * Immutable payload passed to notifiers.
 * Contains no PII (no phone, OTP, or names). Exact GPS coordinates are present
 * only for emergency SOS alerts.
 */
public record AlertMessage(
        Long violationId,
        Long tripId,
        ViolationType type,
        Severity severity,
        String summary,
        Double lat,
        Double lon
) {
    public AlertMessage {
        if (type != ViolationType.SOS) {
            lat = null;
            lon = null;
        }
    }

    public AlertMessage(Long violationId, Long tripId, ViolationType type, Severity severity, String summary) {
        this(violationId, tripId, type, severity, summary, null, null);
    }
}

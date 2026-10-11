package com.example.tripaudit.service;

import com.example.tripaudit.domain.AlertDelivery;
import com.example.tripaudit.domain.Violation;
import java.util.List;

/**
 * Result of attempting to persist a candidate violation.
 */
public record RecordOutcome(
        Status status,
        Violation violation,
        List<AlertDelivery> pendingDeliveries
) {
    public enum Status {
        NEW,
        EXISTING
    }

    public static RecordOutcome existing() {
        return new RecordOutcome(Status.EXISTING, null, List.of());
    }

    public static RecordOutcome newlyCreated(Violation violation, List<AlertDelivery> deliveries) {
        return new RecordOutcome(Status.NEW, violation, List.copyOf(deliveries));
    }
}

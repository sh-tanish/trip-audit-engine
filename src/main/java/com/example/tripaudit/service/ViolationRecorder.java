package com.example.tripaudit.service;

import com.example.tripaudit.alert.AlertRoutingPolicy;
import com.example.tripaudit.config.AuditMetrics;
import com.example.tripaudit.domain.AlertChannel;
import com.example.tripaudit.domain.AlertDelivery;
import com.example.tripaudit.domain.Violation;
import com.example.tripaudit.repository.AlertDeliveryRepository;
import com.example.tripaudit.repository.ViolationRepository;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Commits each candidate violation in its own short, isolated transaction.
 *
 * Runs as a separate bean with @Transactional so that callers without an active
 * transaction (e.g. AuditService, SosService) obtain a fresh transaction per call.
 */
@Component
public class ViolationRecorder {

    private final ViolationRepository violationRepository;
    private final AlertDeliveryRepository alertDeliveryRepository;
    private final AlertRoutingPolicy alertRoutingPolicy;
    private final AuditMetrics auditMetrics;
    private final EntityManager entityManager;

    public ViolationRecorder(ViolationRepository violationRepository,
                             AlertDeliveryRepository alertDeliveryRepository,
                             AlertRoutingPolicy alertRoutingPolicy,
                             AuditMetrics auditMetrics,
                             EntityManager entityManager) {
        this.violationRepository = violationRepository;
        this.alertDeliveryRepository = alertDeliveryRepository;
        this.alertRoutingPolicy = alertRoutingPolicy;
        this.auditMetrics = auditMetrics;
        this.entityManager = entityManager;
    }

    /**
     * Atomically records the violation and its initial PENDING alert rows if not already present.
     *
     * @param violation unsaved violation candidate
     * @return RecordOutcome indicating NEW (with saved entity and pending deliveries) or EXISTING
     */
    @Transactional
    public RecordOutcome recordIfAbsent(Violation violation) {
        if (violationRepository.existsByDedupKey(violation.getDedupKey())) {
            return RecordOutcome.existing();
        }

        // 1. Insert violation
        Violation savedViolation = violationRepository.save(violation);

        // 2. Prepare and insert PENDING AlertDelivery rows based on severity policy
        Set<AlertChannel> channels = alertRoutingPolicy.route(savedViolation.getSeverity());
        List<AlertDelivery> deliveries = new ArrayList<>(channels.size());
        Instant now = Instant.now();

        for (AlertChannel channel : channels) {
            AlertDelivery delivery = new AlertDelivery(savedViolation.getId(), channel, now);
            deliveries.add(alertDeliveryRepository.save(delivery));
        }

        // 3. Flush to force unique constraint validation inside this transaction
        entityManager.flush();

        // 4. Record metric for the newly inserted violation
        auditMetrics.incrementViolation(savedViolation.getType(), savedViolation.getSeverity());

        return RecordOutcome.newlyCreated(savedViolation, deliveries);
    }
}

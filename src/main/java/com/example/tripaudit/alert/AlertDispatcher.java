package com.example.tripaudit.alert;

import com.example.tripaudit.config.AuditMetrics;
import com.example.tripaudit.domain.AlertDelivery;
import com.example.tripaudit.domain.DeliveryStatus;
import com.example.tripaudit.domain.Violation;
import com.example.tripaudit.repository.AlertDeliveryRepository;
import com.example.tripaudit.repository.ViolationRepository;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Dispatches safety alerts to the appropriate notification channels.
 *
 * Implements the Transactional Outbox pattern: violations and their PENDING
 * AlertDelivery rows are committed first. AlertDispatcher then calls external
 * channels. If a notifier throws, the delivery is marked FAILED and failure metrics
 * are recorded without rolling back or losing the persisted violation.
 */
@Component
public class AlertDispatcher {

    private static final Logger log = LoggerFactory.getLogger(AlertDispatcher.class);

    private final NotifierFactory notifierFactory;
    private final AlertDeliveryRepository alertDeliveryRepository;
    private final ViolationRepository violationRepository;
    private final AuditMetrics auditMetrics;

    public AlertDispatcher(NotifierFactory notifierFactory,
                           AlertDeliveryRepository alertDeliveryRepository,
                           ViolationRepository violationRepository,
                           AuditMetrics auditMetrics) {
        this.notifierFactory = notifierFactory;
        this.alertDeliveryRepository = alertDeliveryRepository;
        this.violationRepository = violationRepository;
        this.auditMetrics = auditMetrics;
    }

    /**
     * Dispatches pending alert deliveries for a single newly inserted violation.
     */
    public void dispatch(Violation violation, List<AlertDelivery> deliveries) {
        dispatch(violation, deliveries, null, null);
    }

    /**
     * Dispatches pending alert deliveries with optional GPS coordinates (for SOS).
     */
    public void dispatch(Violation violation, List<AlertDelivery> deliveries, Double lat, Double lon) {
        if (violation == null || deliveries == null || deliveries.isEmpty()) {
            return;
        }

        AlertMessage message = new AlertMessage(
                violation.getId(),
                violation.getTripId(),
                violation.getType(),
                violation.getSeverity(),
                violation.getEvidence(),
                lat,
                lon
        );

        for (AlertDelivery delivery : deliveries) {
            deliverOne(delivery, message);
        }
    }

    /**
     * Recovery path on startup: scans for any PENDING alert deliveries left behind
     * by an unexpected system crash between DB commit and dispatch.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void dispatchPending() {
        List<AlertDelivery> pendingDeliveries = alertDeliveryRepository.findByStatus(DeliveryStatus.PENDING);
        if (pendingDeliveries.isEmpty()) {
            return;
        }

        log.info("Found {} pending alert deliveries left behind. Dispatching recovery...", pendingDeliveries.size());
        for (AlertDelivery delivery : pendingDeliveries) {
            violationRepository.findById(delivery.getViolationId()).ifPresent(violation -> {
                AlertMessage message = new AlertMessage(
                        violation.getId(),
                        violation.getTripId(),
                        violation.getType(),
                        violation.getSeverity(),
                        violation.getEvidence()
                );
                deliverOne(delivery, message);
            });
        }
    }

    private void deliverOne(AlertDelivery delivery, AlertMessage message) {
        try {
            Notifier notifier = notifierFactory.forChannel(delivery.getChannel());
            notifier.send(message);
            delivery.markSent(Instant.now());
            alertDeliveryRepository.save(delivery);
        } catch (Exception ex) {
            String errorMsg = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
            log.error("Failed delivering alert for violation {} to channel {}: {}",
                    delivery.getViolationId(), delivery.getChannel(), errorMsg);
            delivery.markFailed(errorMsg, Instant.now());
            alertDeliveryRepository.save(delivery);
            auditMetrics.incrementAlertsFailed();
        }
    }
}

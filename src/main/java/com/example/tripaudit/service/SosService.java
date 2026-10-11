package com.example.tripaudit.service;

import com.example.tripaudit.alert.AlertDispatcher;
import com.example.tripaudit.config.AuditMetrics;
import com.example.tripaudit.domain.AuditRun;
import com.example.tripaudit.domain.AuditRunKind;
import com.example.tripaudit.domain.GpsFix;
import com.example.tripaudit.domain.Severity;
import com.example.tripaudit.domain.TripEvent;
import com.example.tripaudit.domain.TripEventType;
import com.example.tripaudit.domain.Violation;
import com.example.tripaudit.domain.ViolationType;
import com.example.tripaudit.repository.AuditRunRepository;
import com.example.tripaudit.repository.GpsFixRepository;
import com.example.tripaudit.repository.TripEventRepository;
import com.example.tripaudit.repository.ViolationRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * Fast-path handling for emergency SOS presses.
 *
 * Runs immediately outside the standard audit schedule: finds the latest GPS fix,
 * records an SOS AuditRun and CRITICAL violation, and dispatches immediate alerts
 * to the CONTROL_ROOM and TRANSPORT_MANAGER carrying live coordinates.
 *
 * Not @Transactional: follows the same design as AuditService.
 */
@Service
public class SosService {

    private static final Logger log = LoggerFactory.getLogger(SosService.class);

    private final GpsFixRepository gpsFixRepository;
    private final TripEventRepository tripEventRepository;
    private final AuditRunRepository auditRunRepository;
    private final ViolationRepository violationRepository;
    private final ViolationRecorder violationRecorder;
    private final AlertDispatcher alertDispatcher;
    private final AuditMetrics auditMetrics;

    public SosService(GpsFixRepository gpsFixRepository,
                      TripEventRepository tripEventRepository,
                      AuditRunRepository auditRunRepository,
                      ViolationRepository violationRepository,
                      ViolationRecorder violationRecorder,
                      AlertDispatcher alertDispatcher,
                      AuditMetrics auditMetrics) {
        this.gpsFixRepository = gpsFixRepository;
        this.tripEventRepository = tripEventRepository;
        this.auditRunRepository = auditRunRepository;
        this.violationRepository = violationRepository;
        this.violationRecorder = violationRecorder;
        this.alertDispatcher = alertDispatcher;
        this.auditMetrics = auditMetrics;
    }

    /**
     * Handles an SOS event for a trip from an explicit TripEvent.
     */
    public Violation handleSos(Long tripId, TripEvent event) {
        if (event.getId() == null) {
            tripEventRepository.save(event);
        }
        return processSos(tripId, event.getEmployeeId(), event.getOccurredAt());
    }

    /**
     * Handles an SOS press by tripId, employeeId, and pressedAt timestamp.
     */
    public Violation handleSos(Long tripId, Long employeeId, Instant pressedAt) {
        Instant receivedAt = Instant.now();
        TripEvent event = new TripEvent(tripId, TripEventType.SOS, pressedAt, null, null, employeeId, receivedAt);
        tripEventRepository.save(event);
        return processSos(tripId, employeeId, pressedAt);
    }

    private Violation processSos(Long tripId, Long employeeId, Instant pressedAt) {
        Instant now = Instant.now();

        // 1. AuditRun of kind SOS
        AuditRun auditRun = new AuditRun(tripId, AuditRunKind.SOS, "EMPLOYEE-" + employeeId, now);
        auditRun = auditRunRepository.save(auditRun);

        // 2. Resolve latest GPS fix at or before the press
        Optional<GpsFix> fixOpt = gpsFixRepository
                .findFirstByTripIdAndRecordedAtLessThanEqualOrderByRecordedAtDesc(tripId, pressedAt);

        Double lat = fixOpt.map(GpsFix::getLat).orElse(null);
        Double lon = fixOpt.map(GpsFix::getLon).orElse(null);

        String evidence;
        if (fixOpt.isPresent()) {
            GpsFix fix = fixOpt.get();
            long ageSeconds = Math.max(0, Duration.between(fix.getRecordedAt(), pressedAt).toSeconds());
            evidence = String.format("SOS raised by employee %d at %s. Location: (%.6f, %.6f), fix age: %ds",
                    employeeId, pressedAt, fix.getLat(), fix.getLon(), ageSeconds);
        } else {
            evidence = String.format("SOS raised by employee %d at %s. Location: unknown (no earlier GPS fix recorded)",
                    employeeId, pressedAt);
        }

        // 3. Construct candidate violation
        String subjectKey = employeeId + ":" + pressedAt.toEpochMilli();
        Violation candidate = new Violation(
                tripId,
                ViolationType.SOS,
                Severity.CRITICAL,
                "SOS",
                subjectKey,
                null,
                employeeId,
                evidence,
                now,
                auditRun.getId()
        );

        // 4. Record idempotently
        RecordOutcome outcome;
        try {
            outcome = violationRecorder.recordIfAbsent(candidate);
        } catch (DataIntegrityViolationException ex) {
            log.info("Duplicate SOS press detected for trip {} and subjectKey {}", tripId, subjectKey);
            outcome = RecordOutcome.existing();
        }

        // 5. Alert and update AuditRun
        if (outcome.status() == RecordOutcome.Status.NEW) {
            auditRun.finish(Instant.now(), 1);
            auditRunRepository.save(auditRun);
            auditMetrics.incrementSos();
            alertDispatcher.dispatch(outcome.violation(), outcome.pendingDeliveries(), lat, lon);
            return outcome.violation();
        } else {
            auditRun.finish(Instant.now(), 0);
            auditRunRepository.save(auditRun);
            return violationRepository.findByTripIdOrderByDetectedAtAsc(tripId).stream()
                    .filter(v -> v.getDedupKey().equals(candidate.getDedupKey()))
                    .findFirst()
                    .orElse(candidate);
        }
    }
}

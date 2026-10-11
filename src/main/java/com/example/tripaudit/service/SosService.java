package com.example.tripaudit.service;

import com.example.tripaudit.alert.AlertDispatcher;
import com.example.tripaudit.config.AuditMetrics;
import com.example.tripaudit.audit.DedupKeys;
import com.example.tripaudit.domain.AuditRun;
import com.example.tripaudit.domain.AuditRunKind;
import com.example.tripaudit.domain.GpsFix;
import com.example.tripaudit.domain.Severity;
import com.example.tripaudit.domain.TripEvent;
import com.example.tripaudit.domain.TripEventType;
import com.example.tripaudit.domain.Violation;
import com.example.tripaudit.domain.ViolationType;
import com.example.tripaudit.exception.ForbiddenTripAccessException;
import com.example.tripaudit.exception.NotFoundException;
import com.example.tripaudit.repository.AuditRunRepository;
import com.example.tripaudit.repository.GpsFixRepository;
import com.example.tripaudit.repository.StopRepository;
import com.example.tripaudit.repository.TripEventRepository;
import com.example.tripaudit.repository.TripRepository;
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
    private final TripRepository tripRepository;
    private final StopRepository stopRepository;

    public SosService(GpsFixRepository gpsFixRepository,
                      TripEventRepository tripEventRepository,
                      AuditRunRepository auditRunRepository,
                      ViolationRepository violationRepository,
                      ViolationRecorder violationRecorder,
                      AlertDispatcher alertDispatcher,
                      AuditMetrics auditMetrics,
                      TripRepository tripRepository,
                      StopRepository stopRepository) {
        this.gpsFixRepository = gpsFixRepository;
        this.tripEventRepository = tripEventRepository;
        this.auditRunRepository = auditRunRepository;
        this.violationRepository = violationRepository;
        this.violationRecorder = violationRecorder;
        this.alertDispatcher = alertDispatcher;
        this.auditMetrics = auditMetrics;
        this.tripRepository = tripRepository;
        this.stopRepository = stopRepository;
    }

    /**
     * Handles an SOS event for a trip from an explicit TripEvent.
     */
    public Violation handleSos(Long tripId, TripEvent event) {
        Instant pressedAt = event != null ? event.getOccurredAt() : null;
        Long employeeId = event != null ? event.getEmployeeId() : null;
        return handleSos(tripId, employeeId, pressedAt, event);
    }

    /**
     * Handles an SOS press by tripId, employeeId, and pressedAt timestamp.
     */
    public Violation handleSos(Long tripId, Long employeeId, Instant pressedAt) {
        return handleSos(tripId, employeeId, pressedAt, null);
    }

    private Violation handleSos(Long tripId, Long employeeId, Instant pressedAt, TripEvent providedEvent) {
        // 1. If pressedAt is null, use the server's current time
        Instant effectivePressedAt = pressedAt != null ? pressedAt : Instant.now();

        // 2. Validate before writing anything
        if (!tripRepository.existsById(tripId)) {
            throw new NotFoundException("Trip not found: " + tripId);
        }
        if (employeeId == null || !stopRepository.existsByTripIdAndEmployeeId(tripId, employeeId)) {
            throw new ForbiddenTripAccessException("Employee " + employeeId + " has no stop on trip " + tripId);
        }

        // 3. Compute dedupKey first
        String subjectKey = employeeId + ":" + effectivePressedAt.toEpochMilli();
        String dedupKey = DedupKeys.of(tripId, ViolationType.SOS.name(), subjectKey);

        // If that violation already exists, return existing result without saving a new TripEvent or AuditRun
        if (violationRepository.existsByDedupKey(dedupKey)) {
            return violationRepository.findByDedupKey(dedupKey)
                    .orElseGet(() -> violationRepository.findByTripIdOrderByDetectedAtAsc(tripId).stream()
                            .filter(v -> v.getDedupKey().equals(dedupKey))
                            .findFirst()
                            .orElseThrow());
        }

        // 4. Save TripEvent
        if (providedEvent != null && providedEvent.getId() != null) {
            // Already saved
        } else {
            Instant receivedAt = Instant.now();
            TripEvent eventToSave = (providedEvent != null && providedEvent.getOccurredAt() != null)
                    ? providedEvent
                    : new TripEvent(tripId, TripEventType.SOS, effectivePressedAt, null, null, employeeId, receivedAt);
            tripEventRepository.save(eventToSave);
        }

        // 5. AuditRun of kind SOS
        Instant now = Instant.now();
        AuditRun auditRun = new AuditRun(tripId, AuditRunKind.SOS, "EMPLOYEE-" + employeeId, now);
        auditRun = auditRunRepository.save(auditRun);

        // 6. Resolve latest GPS fix at or before the press
        Optional<GpsFix> fixOpt = gpsFixRepository
                .findFirstByTripIdAndRecordedAtLessThanEqualOrderByRecordedAtDesc(tripId, effectivePressedAt);

        Double lat = fixOpt.map(GpsFix::getLat).orElse(null);
        Double lon = fixOpt.map(GpsFix::getLon).orElse(null);

        String evidence;
        if (fixOpt.isPresent()) {
            GpsFix fix = fixOpt.get();
            long ageSeconds = Math.max(0, Duration.between(fix.getRecordedAt(), effectivePressedAt).toSeconds());
            evidence = String.format("SOS raised by employee %d at %s. Location: (%.6f, %.6f), fix age: %ds",
                    employeeId, effectivePressedAt, fix.getLat(), fix.getLon(), ageSeconds);
        } else {
            evidence = String.format("SOS raised by employee %d at %s. Location: unknown (no earlier GPS fix recorded)",
                    employeeId, effectivePressedAt);
        }

        // 7. Construct candidate violation
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

        // 8. Record idempotently
        RecordOutcome outcome;
        try {
            outcome = violationRecorder.recordIfAbsent(candidate);
        } catch (DataIntegrityViolationException ex) {
            // Treat as existing only if existsByDedupKey is true; otherwise rethrow
            if (violationRepository.existsByDedupKey(candidate.getDedupKey())) {
                log.info("Duplicate SOS press detected for trip {} and subjectKey {}", tripId, subjectKey);
                outcome = RecordOutcome.existing();
            } else {
                throw ex;
            }
        }

        // 9. Alert and update AuditRun
        if (outcome.status() == RecordOutcome.Status.NEW) {
            auditRun.finish(Instant.now(), 1);
            auditRunRepository.save(auditRun);
            auditMetrics.incrementSos();
            alertDispatcher.dispatch(outcome.violation(), outcome.pendingDeliveries(), lat, lon);
            return outcome.violation();
        } else {
            auditRun.finish(Instant.now(), 0);
            auditRunRepository.save(auditRun);
            return violationRepository.findByDedupKey(candidate.getDedupKey())
                    .orElseGet(() -> violationRepository.findByTripIdOrderByDetectedAtAsc(tripId).stream()
                            .filter(v -> v.getDedupKey().equals(candidate.getDedupKey()))
                            .findFirst()
                            .orElse(candidate));
        }
    }
}

package com.example.tripaudit.service;

import com.example.tripaudit.alert.AlertDispatcher;
import com.example.tripaudit.config.AuditMetrics;
import com.example.tripaudit.config.AuditProperties;
import com.example.tripaudit.domain.AuditRun;
import com.example.tripaudit.domain.AuditRunKind;
import com.example.tripaudit.domain.Employee;
import com.example.tripaudit.domain.GpsFix;
import com.example.tripaudit.domain.RouteSegment;
import com.example.tripaudit.domain.Stop;
import com.example.tripaudit.domain.Trip;
import com.example.tripaudit.domain.TripEvent;
import com.example.tripaudit.domain.TripStatus;
import com.example.tripaudit.domain.Violation;
import com.example.tripaudit.exception.ConflictException;
import com.example.tripaudit.exception.NotFoundException;
import com.example.tripaudit.repository.AuditRunRepository;
import com.example.tripaudit.repository.EmployeeRepository;
import com.example.tripaudit.repository.GpsFixRepository;
import com.example.tripaudit.repository.RouteSegmentRepository;
import com.example.tripaudit.repository.StopRepository;
import com.example.tripaudit.repository.TripEventRepository;
import com.example.tripaudit.repository.TripRepository;
import com.example.tripaudit.repository.ViolationRepository;
import com.example.tripaudit.rules.SafetyRule;
import com.example.tripaudit.rules.TripContext;
import com.example.tripaudit.rules.TripContextFactory;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;


@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final TripRepository tripRepository;
    private final StopRepository stopRepository;
    private final RouteSegmentRepository routeSegmentRepository;
    private final GpsFixRepository gpsFixRepository;
    private final TripEventRepository tripEventRepository;
    private final EmployeeRepository employeeRepository;
    private final AuditRunRepository auditRunRepository;
    private final ViolationRepository violationRepository;
    private final List<SafetyRule> safetyRules;
    private final AuditProperties auditProperties;
    private final ViolationRecorder violationRecorder;
    private final AlertDispatcher alertDispatcher;
    private final AuditMetrics auditMetrics;
    private final SosService sosService;

    public AuditService(TripRepository tripRepository,
                        StopRepository stopRepository,
                        RouteSegmentRepository routeSegmentRepository,
                        GpsFixRepository gpsFixRepository,
                        TripEventRepository tripEventRepository,
                        EmployeeRepository employeeRepository,
                        AuditRunRepository auditRunRepository,
                        ViolationRepository violationRepository,
                        List<SafetyRule> safetyRules,
                        AuditProperties auditProperties,
                        ViolationRecorder violationRecorder,
                        AlertDispatcher alertDispatcher,
                        AuditMetrics auditMetrics,
                        SosService sosService) {
        this.tripRepository = tripRepository;
        this.stopRepository = stopRepository;
        this.routeSegmentRepository = routeSegmentRepository;
        this.gpsFixRepository = gpsFixRepository;
        this.tripEventRepository = tripEventRepository;
        this.employeeRepository = employeeRepository;
        this.auditRunRepository = auditRunRepository;
        this.violationRepository = violationRepository;
        this.safetyRules = safetyRules;
        this.auditProperties = auditProperties;
        this.violationRecorder = violationRecorder;
        this.alertDispatcher = alertDispatcher;
        this.auditMetrics = auditMetrics;
        this.sosService = sosService;
    }

    /**
     * Audits a completed trip by id triggered by the default SYSTEM user.
     */
    public AuditRun auditTrip(Long tripId) {
        return auditTrip(tripId, "SYSTEM");
    }

    /**
     * Audits a completed trip with an explicit user / actor.
     *
     * @param tripId ID of the completed trip
     * @param triggeredBy username or subsystem triggering the audit
     * @return recorded AuditRun entity
     */
    public AuditRun auditTrip(Long tripId, String triggeredBy) {
        Instant startTime = Instant.now();

        // 1. Verify trip existence (404)
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new NotFoundException("Trip not found: " + tripId));

        // 2. Verify trip is COMPLETED (409)
        if (trip.getStatus() != TripStatus.COMPLETED) {
            throw new ConflictException("Trip " + tripId + " is not COMPLETED (status: " + trip.getStatus() + ")");
        }

        // 3. Create AuditRun
        AuditRun auditRun = new AuditRun(tripId, AuditRunKind.AUDIT, triggeredBy != null ? triggeredBy : "SYSTEM", startTime);
        auditRun = auditRunRepository.save(auditRun);

        // 4. Load trip facts via explicit repository queries (no lazy loading)
        List<Stop> stops = stopRepository.findByTripIdOrderBySequenceAsc(tripId);
        List<Employee> employees = employeeRepository.findAll();
        List<RouteSegment> segments = routeSegmentRepository.findByTripIdOrderBySeqAsc(tripId);
        List<GpsFix> fixes = gpsFixRepository.findByTripIdOrderByRecordedAtAsc(tripId);
        List<TripEvent> events = tripEventRepository.findByTripIdOrderByOccurredAtAsc(tripId);

        // 5. Assemble immutable TripContext
        TripContext ctx = TripContextFactory.create(
                tripId,
                auditRun.getId(),
                startTime,
                trip.isEscortAssigned(),
                stops,
                employees,
                segments,
                fixes,
                events,
                auditProperties
        );

        // 6. Run all SafetyRule beans (pure logic, no DB)
        List<Violation> candidates = new ArrayList<>();
        for (SafetyRule rule : safetyRules) {
            List<Violation> detected = rule.evaluate(ctx);
            if (detected != null) {
                candidates.addAll(detected);
            }
        }

        // 7. Store candidate violations idempotently (insert-if-absent)
        List<RecordOutcome> newOutcomes = new ArrayList<>();
        for (Violation candidate : candidates) {
            try {
                RecordOutcome outcome = violationRecorder.recordIfAbsent(candidate);
                if (outcome.status() == RecordOutcome.Status.NEW) {
                    newOutcomes.add(outcome);
                }
            } catch (DataIntegrityViolationException ex) {
                // Caught outside ViolationRecorder's transaction: treat as EXISTING
                log.debug("Concurrent insert collision for violation dedupKey: {}", candidate.getDedupKey());
            }
        }

        // 8. Update and finish AuditRun
        auditRun.finish(Instant.now(), newOutcomes.size());
        auditRun = auditRunRepository.save(auditRun);
        auditMetrics.incrementAudits();

        // 9. Dispatch alerts ONLY for newly inserted violations
        for (RecordOutcome outcome : newOutcomes) {
            alertDispatcher.dispatch(outcome.violation(), outcome.pendingDeliveries());
        }

        return auditRun;
    }

    /**
     * Delegates SOS emergency event processing to SosService.
     */
    public Violation handleSos(Long tripId, TripEvent event) {
        return sosService.handleSos(tripId, event);
    }

    /**
     * Delegates SOS emergency press processing to SosService.
     */
    public Violation handleSos(Long tripId, Long employeeId, Instant pressedAt) {
        return sosService.handleSos(tripId, employeeId, pressedAt);
    }

    /**
     * Returns all detected violations for a trip.
     */
    public List<Violation> getTripViolations(Long tripId) {
        return violationRepository.findByTripIdOrderByDetectedAtAsc(tripId);
    }
}

package com.example.tripaudit.service;

import com.example.tripaudit.domain.AuditRun;
import com.example.tripaudit.domain.Driver;
import com.example.tripaudit.domain.Employee;
import com.example.tripaudit.domain.Gender;
import com.example.tripaudit.domain.GeoPoint;
import com.example.tripaudit.domain.GpsFix;
import com.example.tripaudit.domain.RouteSegment;
import com.example.tripaudit.domain.Severity;
import com.example.tripaudit.domain.Stop;
import com.example.tripaudit.domain.StopType;
import com.example.tripaudit.domain.Trip;
import com.example.tripaudit.domain.TripEvent;
import com.example.tripaudit.domain.TripEventType;
import com.example.tripaudit.domain.Vehicle;
import com.example.tripaudit.domain.Violation;
import com.example.tripaudit.domain.ViolationType;
import com.example.tripaudit.repository.DriverRepository;
import com.example.tripaudit.repository.EmployeeRepository;
import com.example.tripaudit.repository.GpsFixRepository;
import com.example.tripaudit.repository.RouteSegmentRepository;
import com.example.tripaudit.repository.StopRepository;
import com.example.tripaudit.repository.TripEventRepository;
import com.example.tripaudit.repository.TripRepository;
import com.example.tripaudit.repository.VehicleRepository;
import com.example.tripaudit.repository.ViolationRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class EdgeCaseAuditIntegrationTest {

    @Autowired
    private AuditService auditService;

    @Autowired
    private TripRepository tripRepository;

    @Autowired
    private DriverRepository driverRepository;

    @Autowired
    private VehicleRepository vehicleRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private StopRepository stopRepository;

    @Autowired
    private RouteSegmentRepository routeSegmentRepository;

    @Autowired
    private GpsFixRepository gpsFixRepository;

    @Autowired
    private TripEventRepository tripEventRepository;

    @Autowired
    private ViolationRepository violationRepository;

    private String randomSuffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    @Test
    @DisplayName("Case study example 1: Trip A produces NIGHT_SAFETY violation (HIGH) on last drop")
    void auditSeededTripA_detectsNightSafety() {
        Driver driverA = driverRepository.findByDriverCode("DRV-101").orElseThrow();
        Trip tripA = tripRepository.findAll().stream()
                .filter(t -> t.getDriverId().equals(driverA.getId()))
                .findFirst()
                .orElseThrow();

        AuditRun run = auditService.auditTrip(tripA.getId(), "ADMIN");
        assertNotNull(run);

        List<Violation> violations = violationRepository.findByTripIdOrderByDetectedAtAsc(tripA.getId());
        assertEquals(1, violations.size());

        Violation v = violations.getFirst();
        assertEquals(ViolationType.NIGHT_SAFETY, v.getType());
        assertEquals(Severity.HIGH, v.getSeverity());
        assertTrue(v.getEvidence().contains("Last drop at 23:10"));
        assertTrue(v.getEvidence().contains("passenger alone, no escort — escort required"));
        assertFalse(v.getEvidence().contains("EMP-102"));
    }

    @Test
    @DisplayName("Case study example 2: Trip B produces ROUTE_DEVIATION violation (HIGH) for 4 min off-route")
    void auditSeededTripB_detectsRouteDeviation() {
        Driver driverB = driverRepository.findByDriverCode("DRV-102").orElseThrow();
        Trip tripB = tripRepository.findAll().stream()
                .filter(t -> t.getDriverId().equals(driverB.getId()))
                .findFirst()
                .orElseThrow();

        AuditRun run = auditService.auditTrip(tripB.getId(), "ADMIN");
        assertNotNull(run);

        List<Violation> violations = violationRepository.findByTripIdOrderByDetectedAtAsc(tripB.getId());
        assertEquals(1, violations.size());

        Violation v = violations.getFirst();
        assertEquals(ViolationType.ROUTE_DEVIATION, v.getType());
        assertEquals(Severity.HIGH, v.getSeverity());
        assertTrue(v.getEvidence().contains("Route deviation: max"));
        assertTrue(v.getEvidence().contains("near segment 0"));
    }

    @Test
    @DisplayName("Trip C: Ingested OTP mismatch produces OTP_MISMATCH violation (HIGH)")
    void auditSeededTripC_detectsOtpMismatch() {
        Driver driverC = driverRepository.findByDriverCode("DRV-103").orElseThrow();
        Trip tripC = tripRepository.findAll().stream()
                .filter(t -> t.getDriverId().equals(driverC.getId()))
                .findFirst()
                .orElseThrow();

        AuditRun run = auditService.auditTrip(tripC.getId(), "ADMIN");
        assertNotNull(run);

        List<Violation> violations = violationRepository.findByTripIdOrderByDetectedAtAsc(tripC.getId());
        assertEquals(1, violations.size());

        Violation v = violations.getFirst();
        assertEquals(ViolationType.OTP_MISMATCH, v.getType());
        assertEquals(Severity.HIGH, v.getSeverity());
        assertTrue(v.getEvidence().contains("OTP verification failed (MISMATCH)"));
    }

    @Test
    @DisplayName("Trip D: Extreme over-speed (>1.5x limit) produces OVER_SPEED violation (CRITICAL)")
    void auditSeededTripD_detectsExtremeOverSpeed() {
        Driver driverD = driverRepository.findByDriverCode("DRV-104").orElseThrow();
        Trip tripD = tripRepository.findAll().stream()
                .filter(t -> t.getDriverId().equals(driverD.getId()))
                .findFirst()
                .orElseThrow();

        AuditRun run = auditService.auditTrip(tripD.getId(), "ADMIN");
        assertNotNull(run);

        List<Violation> violations = violationRepository.findByTripIdOrderByDetectedAtAsc(tripD.getId());
        assertEquals(1, violations.size());

        Violation v = violations.getFirst();
        assertEquals(ViolationType.OVER_SPEED, v.getType());
        assertEquals(Severity.CRITICAL, v.getSeverity());
        assertTrue(v.getEvidence().contains("peak 65.0 km/h in 40.0 km/h zone"));
    }

    @Test
    @DisplayName("Trip E: Clean trip produces 0 violations")
    void auditSeededTripE_cleanTripYieldsZeroViolations() {
        Driver driverE = driverRepository.findByDriverCode("DRV-105").orElseThrow();
        Trip tripE = tripRepository.findAll().stream()
                .filter(t -> t.getDriverId().equals(driverE.getId()))
                .findFirst()
                .orElseThrow();

        AuditRun run = auditService.auditTrip(tripE.getId(), "ADMIN");
        assertNotNull(run);
        assertEquals(0, run.getNewViolations());

        List<Violation> violations = violationRepository.findByTripIdOrderByDetectedAtAsc(tripE.getId());
        assertEquals(0, violations.size());
    }

    @Test
    @DisplayName("Empty GPS list on completed trip executes cleanly with 0 GPS violations")
    void auditTripWithEmptyGps_completesWithoutCrash() {
        String suffix = randomSuffix();
        Driver driver = driverRepository.save(new Driver("DRV-EGPS-" + suffix, "Driver " + suffix));
        Vehicle vehicle = vehicleRepository.save(new Vehicle("KA01EGPS" + suffix));
        Employee emp = employeeRepository.save(new Employee("EMP-EGPS-" + suffix, Gender.MALE));

        Instant pickupTime = Instant.parse("2026-10-11T08:00:00Z"); // Daytime (13:30 IST)
        Instant dropTime = Instant.parse("2026-10-11T08:30:00Z");

        Trip trip = new Trip(driver.getId(), vehicle.getId(), false, pickupTime);
        trip.complete(dropTime.plusSeconds(30));
        trip = tripRepository.save(trip);

        Stop s1 = stopRepository.save(new Stop(trip.getId(), 1, StopType.PICKUP, emp.getId(), pickupTime, null));
        Stop s2 = stopRepository.save(new Stop(trip.getId(), 2, StopType.DROP, emp.getId(), dropTime, "1234"));

        routeSegmentRepository.save(new RouteSegment(
                trip.getId(), 0, new GeoPoint(12.9716, 77.5900), new GeoPoint(12.9716, 77.6500), 50.0
        ));

        // OTP matched at drop
        tripEventRepository.save(new TripEvent(
                trip.getId(), TripEventType.OTP_ENTERED, dropTime.plusSeconds(10), s2.getId(), true, null, dropTime.plusSeconds(10)
        ));
        tripEventRepository.save(new TripEvent(
                trip.getId(), TripEventType.TRIP_COMPLETED, dropTime.plusSeconds(30), null, null, null, dropTime.plusSeconds(30)
        ));

        // ZERO GPS fixes added!
        AuditRun run = auditService.auditTrip(trip.getId(), "ADMIN");
        assertNotNull(run);
        assertEquals(0, run.getNewViolations(), "Empty GPS list should not trigger false violations or crash");
    }

    @Test
    @DisplayName("Unsorted GPS fixes in DB are sorted chronologically and detect sustained route deviation")
    void auditTripWithUnsortedGpsFixes_correctlySortsAndDetectsDeviation() {
        String suffix = randomSuffix();
        Driver driver = driverRepository.save(new Driver("DRV-UGPS-" + suffix, "Driver " + suffix));
        Vehicle vehicle = vehicleRepository.save(new Vehicle("KA01UGPS" + suffix));
        Employee emp = employeeRepository.save(new Employee("EMP-UGPS-" + suffix, Gender.MALE));

        Instant baseTime = Instant.parse("2026-10-11T08:00:00Z");
        Instant dropTime = baseTime.plusSeconds(600);

        Trip trip = new Trip(driver.getId(), vehicle.getId(), false, baseTime);
        trip.complete(dropTime);
        trip = tripRepository.save(trip);

        Stop s1 = stopRepository.save(new Stop(trip.getId(), 1, StopType.PICKUP, emp.getId(), baseTime, null));
        Stop s2 = stopRepository.save(new Stop(trip.getId(), 2, StopType.DROP, emp.getId(), dropTime, "1234"));

        routeSegmentRepository.save(new RouteSegment(
                trip.getId(), 0, new GeoPoint(12.9716, 77.5900), new GeoPoint(12.9716, 77.6500), 50.0
        ));

        tripEventRepository.save(new TripEvent(
                trip.getId(), TripEventType.OTP_ENTERED, dropTime, s2.getId(), true, null, dropTime
        ));
        tripEventRepository.save(new TripEvent(
                trip.getId(), TripEventType.TRIP_COMPLETED, dropTime, null, null, null, dropTime
        ));

        // Create 5 off-route fixes 30s apart (duration 120s >= 120s) at 800m north (12.9788)
        // Insert them out of order: t=60s, t=0s, t=120s, t=30s, t=90s
        gpsFixRepository.save(new GpsFix(trip.getId(), baseTime.plusSeconds(60), 12.9788, 77.6020, 30.0));
        gpsFixRepository.save(new GpsFix(trip.getId(), baseTime, 12.9788, 77.6000, 30.0));
        gpsFixRepository.save(new GpsFix(trip.getId(), baseTime.plusSeconds(120), 12.9788, 77.6040, 30.0));
        gpsFixRepository.save(new GpsFix(trip.getId(), baseTime.plusSeconds(30), 12.9788, 77.6010, 30.0));
        gpsFixRepository.save(new GpsFix(trip.getId(), baseTime.plusSeconds(90), 12.9788, 77.6030, 30.0));

        AuditRun run = auditService.auditTrip(trip.getId(), "ADMIN");
        assertNotNull(run);

        List<Violation> violations = violationRepository.findByTripIdOrderByDetectedAtAsc(trip.getId());
        assertEquals(1, violations.size());
        assertEquals(ViolationType.ROUTE_DEVIATION, violations.getFirst().getType());
        assertEquals(String.valueOf(baseTime.toEpochMilli()), violations.getFirst().getSubjectKey(),
                "SubjectKey must match the earliest fix timestamp despite shuffled DB arrival");
    }

    @Test
    @DisplayName("Duplicate OTP events for the same stop and timestamp generate only one violation")
    void auditTripWithDuplicateOtpEvents_deduplicatesToOneViolation() {
        String suffix = randomSuffix();
        Driver driver = driverRepository.save(new Driver("DRV-DOTP-" + suffix, "Driver " + suffix));
        Vehicle vehicle = vehicleRepository.save(new Vehicle("KA01DOTP" + suffix));
        Employee emp = employeeRepository.save(new Employee("EMP-DOTP-" + suffix, Gender.MALE));

        Instant baseTime = Instant.parse("2026-10-11T08:00:00Z");
        Instant dropTime = baseTime.plusSeconds(600);

        Trip trip = new Trip(driver.getId(), vehicle.getId(), false, baseTime);
        trip.complete(dropTime);
        trip = tripRepository.save(trip);

        stopRepository.save(new Stop(trip.getId(), 1, StopType.PICKUP, emp.getId(), baseTime, null));
        Stop drop = stopRepository.save(new Stop(trip.getId(), 2, StopType.DROP, emp.getId(), dropTime, "1234"));

        routeSegmentRepository.save(new RouteSegment(
                trip.getId(), 0, new GeoPoint(12.9716, 77.5900), new GeoPoint(12.9716, 77.6500), 50.0
        ));

        // Two identical OTP mismatch events (e.g. repeated network callback with same occurredAt)
        tripEventRepository.save(new TripEvent(
                trip.getId(), TripEventType.OTP_ENTERED, dropTime, drop.getId(), false, null, dropTime
        ));
        tripEventRepository.save(new TripEvent(
                trip.getId(), TripEventType.OTP_ENTERED, dropTime, drop.getId(), false, null, dropTime.plusSeconds(1)
        ));
        tripEventRepository.save(new TripEvent(
                trip.getId(), TripEventType.TRIP_COMPLETED, dropTime.plusSeconds(2), null, null, null, dropTime.plusSeconds(2)
        ));

        AuditRun run = auditService.auditTrip(trip.getId(), "ADMIN");
        assertNotNull(run);

        List<Violation> violations = violationRepository.findByTripIdOrderByDetectedAtAsc(trip.getId());
        assertEquals(1, violations.size(), "Duplicate OTP events must result in exactly 1 violation for that stop");
        assertEquals(ViolationType.OTP_MISMATCH, violations.getFirst().getType());
    }
}

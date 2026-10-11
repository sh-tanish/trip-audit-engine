package com.example.tripaudit.config;

import com.example.tripaudit.domain.*;
import com.example.tripaudit.repository.*;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class SeedDataLoaderTest {

    @Autowired
    private TripRepository tripRepository;

    @Autowired
    private StopRepository stopRepository;

    @Autowired
    private GpsFixRepository gpsFixRepository;

    @Autowired
    private TripEventRepository tripEventRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private DriverRepository driverRepository;

    @Autowired
    private AuditProperties auditProperties;

    @Test
    @DisplayName("AuditProperties are bound correctly from YAML defaults")
    void auditPropertiesAreBound() {
        assertNotNull(auditProperties);
        assertEquals("Asia/Kolkata", auditProperties.zone().getId());
        assertEquals("22:00", auditProperties.night().start().toString());
        assertEquals("06:00", auditProperties.night().end().toString());
        assertEquals(500.0, auditProperties.route().thresholdMeters());
        assertEquals(Duration.ofSeconds(120), auditProperties.route().minDuration());
        assertEquals(Duration.ofSeconds(30), auditProperties.speed().minDuration());
        assertEquals(1.5, auditProperties.speed().criticalFactor());
        assertEquals(Duration.ofSeconds(60), auditProperties.gps().maxGap());
    }

    @Test
    @DisplayName("Dev profile seeds exactly 5 trips, all completed with 30s GPS fixes")
    void devProfileSeedsAllFiveTrips() {
        List<Trip> trips = tripRepository.findAll();
        assertEquals(5, trips.size(), "Must have seeded exactly 5 trips");

        for (Trip trip : trips) {
            // Every seeded trip must be marked COMPLETED and have a completed timestamp
            assertEquals(TripStatus.COMPLETED, trip.getStatus());
            assertNotNull(trip.getCompletedAt());

            // Every seeded trip has a TRIP_COMPLETED event
            List<TripEvent> events = tripEventRepository.findByTripIdOrderByOccurredAtAsc(trip.getId());
            boolean hasCompletedEvent = events.stream()
                    .anyMatch(e -> e.getType() == TripEventType.TRIP_COMPLETED);
            assertTrue(hasCompletedEvent, "Trip " + trip.getId() + " must have a TRIP_COMPLETED event");

            // GPS fixes every 30 seconds
            List<GpsFix> fixes = gpsFixRepository.findByTripIdOrderByRecordedAtAsc(trip.getId());
            assertFalse(fixes.isEmpty(), "Trip " + trip.getId() + " must have GPS fixes");
            for (int i = 0; i < fixes.size() - 1; i++) {
                Instant current = fixes.get(i).getRecordedAt();
                Instant next = fixes.get(i + 1).getRecordedAt();
                assertEquals(Duration.ofSeconds(30), Duration.between(current, next),
                        "Fixes must be exactly 30s apart for trip " + trip.getId());
            }
        }
    }

    @Test
    @DisplayName("Trip A: Last drop is a woman at 11:10 PM with no escort")
    void tripAHasNightSafetyScenario() {
        // Find Trip A: escortAssigned is false and has female employee EMP-102 dropped last
        Trip tripA = tripRepository.findAll().stream()
                .filter(t -> !t.isEscortAssigned())
                .filter(t -> {
                    List<Stop> stops = stopRepository.findByTripIdOrderBySequenceAsc(t.getId());
                    if (stops.size() < 4) return false;
                    Stop lastStop = stops.get(stops.size() - 1);
                    Employee emp = employeeRepository.findById(lastStop.getEmployeeId()).orElseThrow();
                    return emp.getGender() == Gender.FEMALE;
                })
                .findFirst()
                .orElseThrow();

        List<Stop> stops = stopRepository.findByTripIdOrderBySequenceAsc(tripA.getId());
        Stop lastDrop = stops.get(stops.size() - 1);
        assertEquals(StopType.DROP, lastDrop.getType());
        // 17:40:00Z corresponds to 23:10:00+05:30 (11:10 PM IST)
        assertEquals(Instant.parse("2026-10-11T17:40:00Z"), lastDrop.getPlannedTime());
        assertFalse(tripA.isEscortAssigned());
    }

    @Test
    @DisplayName("Trip B: Off-route deviation adds 0.0072 deg latitude for 9 consecutive fixes (4 minutes)")
    void tripBHasRouteDeviationScenario() {
        // Trip B has fixes with lat == 12.9788 (12.9716 + 0.0072)
        Trip tripB = tripRepository.findAll().stream()
                .filter(t -> {
                    List<GpsFix> fixes = gpsFixRepository.findByTripIdOrderByRecordedAtAsc(t.getId());
                    return fixes.stream().anyMatch(f -> Math.abs(f.getLat() - 12.9788) < 0.0001);
                })
                .findFirst()
                .orElseThrow();

        List<GpsFix> fixes = gpsFixRepository.findByTripIdOrderByRecordedAtAsc(tripB.getId());
        List<GpsFix> deviatingFixes = fixes.stream()
                .filter(f -> Math.abs(f.getLat() - 12.9788) < 0.0001)
                .toList();

        assertEquals(9, deviatingFixes.size(), "Deviation must be 9 consecutive fixes");
        Instant firstDev = deviatingFixes.get(0).getRecordedAt();
        Instant lastDev = deviatingFixes.get(8).getRecordedAt();
        assertEquals(Duration.ofMinutes(4), Duration.between(firstDev, lastDev),
                "9 consecutive fixes 30s apart must span exactly 4 minutes (240s)");
    }

    @Test
    @DisplayName("Trip C: Has an OTP mismatch event and frozen stop")
    void tripCHasOtpMismatchScenario() {
        Trip tripC = tripRepository.findAll().stream()
                .filter(t -> {
                    List<TripEvent> events = tripEventRepository.findByTripIdOrderByOccurredAtAsc(t.getId());
                    return events.stream().anyMatch(e -> Boolean.FALSE.equals(e.getOtpMatched()));
                })
                .findFirst()
                .orElseThrow();

        List<Stop> stops = stopRepository.findByTripIdOrderBySequenceAsc(tripC.getId());
        Stop dropStop = stops.stream().filter(s -> s.getType() == StopType.DROP).findFirst().orElseThrow();
        assertEquals(StopStatus.FROZEN, dropStop.getStatus(), "Mismatched OTP stop must be FROZEN");
    }

    @Test
    @DisplayName("Trip D: Has sustained over-speed fixes > 40 km/h lasting >= 30 seconds")
    void tripDHasSustainedOverSpeedScenario() {
        Trip tripD = tripRepository.findAll().stream()
                .filter(t -> {
                    List<GpsFix> fixes = gpsFixRepository.findByTripIdOrderByRecordedAtAsc(t.getId());
                    return fixes.stream().anyMatch(f -> f.getSpeedKmph() > 60.0);
                })
                .findFirst()
                .orElseThrow();

        List<GpsFix> speedingFixes = gpsFixRepository.findByTripIdOrderByRecordedAtAsc(tripD.getId()).stream()
                .filter(f -> f.getSpeedKmph() > 60.0)
                .toList();

        assertTrue(speedingFixes.size() >= 2, "Must have consecutive speeding fixes");
        Duration duration = Duration.between(speedingFixes.get(0).getRecordedAt(),
                speedingFixes.get(speedingFixes.size() - 1).getRecordedAt());
        assertTrue(duration.toSeconds() >= 30, "Speeding run must last at least 30s");
    }

    @Test
    @DisplayName("Trip E: Clean trip uses midday times and normal speeds along route")
    void tripEIsCleanMidday() {
        Driver driverE = driverRepository.findByDriverCode("DRV-105").orElseThrow();
        Trip tripE = tripRepository.findAll().stream()
                .filter(t -> t.getDriverId().equals(driverE.getId()))
                .findFirst()
                .orElseThrow();

        List<Stop> stops = stopRepository.findByTripIdOrderBySequenceAsc(tripE.getId());
        // Midday: 06:30Z is 12:00 PM IST
        assertEquals(Instant.parse("2026-10-11T06:30:00Z"), stops.get(0).getPlannedTime());

        List<GpsFix> fixes = gpsFixRepository.findByTripIdOrderByRecordedAtAsc(tripE.getId());
        assertTrue(fixes.stream().allMatch(f -> Math.abs(f.getLat() - 12.9716) < 0.0001 && f.getSpeedKmph() <= 35.0));
    }
}

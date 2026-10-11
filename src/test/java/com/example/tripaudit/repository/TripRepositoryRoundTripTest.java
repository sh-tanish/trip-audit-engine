package com.example.tripaudit.repository;

import com.example.tripaudit.domain.*;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class TripRepositoryRoundTripTest {

    @Autowired
    private DriverRepository driverRepository;

    @Autowired
    private VehicleRepository vehicleRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private TripRepository tripRepository;

    @Autowired
    private StopRepository stopRepository;

    @Autowired
    private RouteSegmentRepository routeSegmentRepository;

    @Autowired
    private GpsFixRepository gpsFixRepository;

    @Autowired
    private TripEventRepository tripEventRepository;

    @Autowired
    private AlertDeliveryRepository alertDeliveryRepository;

    @Autowired
    private ViolationRepository violationRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("Save and reload a complete trip graph with stops, segments, and GPS fixes via explicit queries")
    void saveAndReloadTripWithStopsAndGps() {
        Driver driver = driverRepository.save(new Driver("DRV-RT-1", "Test Driver"));
        Vehicle vehicle = vehicleRepository.save(new Vehicle("KA01RT9999"));
        Employee emp1 = employeeRepository.save(new Employee("EMP-RT-1", Gender.FEMALE));
        Employee emp2 = employeeRepository.save(new Employee("EMP-RT-2", Gender.MALE));

        Instant now = Instant.parse("2026-10-11T12:00:00Z");
        Trip trip = new Trip(driver.getId(), vehicle.getId(), false, now);
        trip = tripRepository.save(trip);
        Long tripId = trip.getId();

        // Stops
        Stop s1 = new Stop(tripId, 1, StopType.PICKUP, emp1.getId(), now.plusSeconds(60), null);
        Stop s2 = new Stop(tripId, 2, StopType.PICKUP, emp2.getId(), now.plusSeconds(120), null);
        Stop s3 = new Stop(tripId, 3, StopType.DROP, emp1.getId(), now.plusSeconds(600), "1234");
        Stop s4 = new Stop(tripId, 4, StopType.DROP, emp2.getId(), now.plusSeconds(900), "5678");
        stopRepository.saveAll(List.of(s1, s2, s3, s4));

        // Route Segments
        RouteSegment seg0 = new RouteSegment(tripId, 0, new GeoPoint(12.9716, 77.5900), new GeoPoint(12.9716, 77.6200), 50.0);
        RouteSegment seg1 = new RouteSegment(tripId, 1, new GeoPoint(12.9716, 77.6200), new GeoPoint(12.9716, 77.6500), 40.0);
        routeSegmentRepository.saveAll(List.of(seg0, seg1));

        // GPS Fixes
        GpsFix fix1 = new GpsFix(tripId, now.plusSeconds(30), 12.9716, 77.5950, 42.0);
        GpsFix fix2 = new GpsFix(tripId, now.plusSeconds(60), 12.9716, 77.6000, 45.0);
        gpsFixRepository.saveAll(List.of(fix1, fix2));

        // Events
        TripEvent event = new TripEvent(tripId, TripEventType.TRIP_COMPLETED, now.plusSeconds(950), null, null, null, now.plusSeconds(950));
        tripEventRepository.save(event);

        entityManager.flush();
        entityManager.clear();

        // Reload and verify
        Trip reloadedTrip = tripRepository.findById(tripId).orElseThrow();
        assertEquals(driver.getId(), reloadedTrip.getDriverId());
        assertEquals(vehicle.getId(), reloadedTrip.getVehicleId());
        assertEquals(TripStatus.ACTIVE, reloadedTrip.getStatus());

        List<Stop> reloadedStops = stopRepository.findByTripIdOrderBySequenceAsc(tripId);
        assertEquals(4, reloadedStops.size());
        assertEquals(1, reloadedStops.get(0).getSequence());
        assertEquals(StopType.PICKUP, reloadedStops.get(0).getType());
        assertEquals(4, reloadedStops.get(3).getSequence());
        assertEquals("5678", reloadedStops.get(3).getExpectedOtp());

        List<RouteSegment> reloadedSegments = routeSegmentRepository.findByTripIdOrderBySeqAsc(tripId);
        assertEquals(2, reloadedSegments.size());
        assertEquals(50.0, reloadedSegments.get(0).getSpeedLimitKmph());
        assertEquals(40.0, reloadedSegments.get(1).getSpeedLimitKmph());

        List<GpsFix> reloadedFixes = gpsFixRepository.findByTripIdOrderByRecordedAtAsc(tripId);
        assertEquals(2, reloadedFixes.size());
        assertEquals(42.0, reloadedFixes.get(0).getSpeedKmph());
        assertEquals(45.0, reloadedFixes.get(1).getSpeedKmph());

        List<TripEvent> reloadedEvents = tripEventRepository.findByTripIdOrderByOccurredAtAsc(tripId);
        assertEquals(1, reloadedEvents.size());
        assertEquals(TripEventType.TRIP_COMPLETED, reloadedEvents.get(0).getType());
    }

    @Test
    @DisplayName("Unique constraint on STOP (trip_id, sequence) is enforced")
    void duplicateStopSequenceRejected() {
        Trip trip = tripRepository.save(new Trip(1L, 1L, false, Instant.now()));
        Stop s1 = new Stop(trip.getId(), 1, StopType.PICKUP, 1L, Instant.now(), null);
        Stop s2 = new Stop(trip.getId(), 1, StopType.DROP, 1L, Instant.now(), null);

        stopRepository.save(s1);
        entityManager.flush();

        assertThrows(DataIntegrityViolationException.class, () -> stopRepository.save(s2));
    }

    @Test
    @DisplayName("Unique constraint on GPS_FIX (trip_id, recorded_at) is enforced")
    void duplicateGpsFixTimeRejected() {
        Trip trip = tripRepository.save(new Trip(1L, 1L, false, Instant.now()));
        Instant time = Instant.parse("2026-10-11T12:00:00Z");
        GpsFix f1 = new GpsFix(trip.getId(), time, 12.0, 77.0, 30.0);
        GpsFix f2 = new GpsFix(trip.getId(), time, 12.1, 77.1, 35.0);

        gpsFixRepository.save(f1);
        entityManager.flush();

        assertThrows(DataIntegrityViolationException.class, () -> gpsFixRepository.save(f2));
    }

    @Test
    @DisplayName("Unique constraint on ROUTE_SEGMENT (trip_id, seq) is enforced")
    void duplicateRouteSegmentSeqRejected() {
        Trip trip = tripRepository.save(new Trip(1L, 1L, false, Instant.now()));
        RouteSegment seg1 = new RouteSegment(trip.getId(), 0, new GeoPoint(12.0, 77.0), new GeoPoint(12.1, 77.1), 50.0);
        RouteSegment seg2 = new RouteSegment(trip.getId(), 0, new GeoPoint(12.2, 77.2), new GeoPoint(12.3, 77.3), 40.0);

        routeSegmentRepository.save(seg1);
        entityManager.flush();

        assertThrows(DataIntegrityViolationException.class, () -> routeSegmentRepository.save(seg2));
    }

    @Test
    @DisplayName("Unique constraint on ALERT_DELIVERY (violation_id, channel) is enforced")
    void duplicateAlertDeliveryChannelRejected() {
        Violation v = violationRepository.save(new Violation(1L, ViolationType.SOS, Severity.CRITICAL,
                "SosRule", "emp-1", null, 1L, "Panic press", Instant.now(), "run-1"));
        entityManager.flush();

        AlertDelivery d1 = new AlertDelivery(v.getId(), AlertChannel.CONTROL_ROOM, Instant.now());
        AlertDelivery d2 = new AlertDelivery(v.getId(), AlertChannel.CONTROL_ROOM, Instant.now());

        alertDeliveryRepository.save(d1);
        entityManager.flush();

        assertThrows(DataIntegrityViolationException.class, () -> alertDeliveryRepository.save(d2));
    }
}

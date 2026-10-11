package com.example.tripaudit.service;

import com.example.tripaudit.alert.NotifierFactory;
import com.example.tripaudit.alert.RecordingNotifier;
import com.example.tripaudit.domain.AlertChannel;
import com.example.tripaudit.domain.AlertDelivery;
import com.example.tripaudit.domain.Driver;
import com.example.tripaudit.domain.GpsFix;
import com.example.tripaudit.domain.Severity;
import com.example.tripaudit.domain.Trip;
import com.example.tripaudit.domain.Vehicle;
import com.example.tripaudit.domain.Violation;
import com.example.tripaudit.domain.ViolationType;
import com.example.tripaudit.repository.AlertDeliveryRepository;
import com.example.tripaudit.repository.DriverRepository;
import com.example.tripaudit.repository.GpsFixRepository;
import com.example.tripaudit.repository.TripRepository;
import com.example.tripaudit.repository.VehicleRepository;
import com.example.tripaudit.repository.ViolationRepository;
import java.time.Instant;
import java.util.List;
import com.example.tripaudit.domain.Stop;
import com.example.tripaudit.domain.StopType;
import com.example.tripaudit.exception.ForbiddenTripAccessException;
import com.example.tripaudit.exception.NotFoundException;
import com.example.tripaudit.repository.AuditRunRepository;
import com.example.tripaudit.repository.StopRepository;
import com.example.tripaudit.repository.TripEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class SosServiceIntegrationTest {

    @Autowired
    private SosService sosService;

    @Autowired
    private TripRepository tripRepository;

    @Autowired
    private DriverRepository driverRepository;

    @Autowired
    private VehicleRepository vehicleRepository;

    @Autowired
    private GpsFixRepository gpsFixRepository;

    @Autowired
    private ViolationRepository violationRepository;

    @Autowired
    private AlertDeliveryRepository alertDeliveryRepository;

    @Autowired
    private StopRepository stopRepository;

    @Autowired
    private TripEventRepository tripEventRepository;

    @Autowired
    private AuditRunRepository auditRunRepository;

    @Autowired
    private NotifierFactory notifierFactory;

    private RecordingNotifier controlRoomNotifier;
    private RecordingNotifier transportManagerNotifier;

    @BeforeEach
    void setUp() {
        controlRoomNotifier = new RecordingNotifier(AlertChannel.CONTROL_ROOM);
        transportManagerNotifier = new RecordingNotifier(AlertChannel.TRANSPORT_MANAGER);
        notifierFactory.register(controlRoomNotifier);
        notifierFactory.register(transportManagerNotifier);
    }

    @Test
    @DisplayName("SOS creates a CRITICAL violation carrying the latest earlier fix, routes to control room & manager, and exact retry deduplicates")
    void sosWithGpsAndExactRetryDeduplication() {
        Driver driver = driverRepository.save(new Driver("DRV-SOS-1", "SOS Driver"));
        Vehicle vehicle = vehicleRepository.save(new Vehicle("KA01SOS1234"));
        Trip trip = tripRepository.save(new Trip(driver.getId(), vehicle.getId(), false, Instant.now()));
        stopRepository.save(new Stop(trip.getId(), 1, StopType.PICKUP, 555L, Instant.now(), null));

        Instant pressTime = Instant.parse("2026-10-11T20:00:00Z");

        // 1. Earlier GPS fix (10s before press)
        GpsFix earlierFix = gpsFixRepository.save(new GpsFix(
                trip.getId(), pressTime.minusSeconds(10), 12.9716, 77.5946, 40.0
        ));

        // Later GPS fix (10s after press, should NOT be picked)
        gpsFixRepository.save(new GpsFix(
                trip.getId(), pressTime.plusSeconds(10), 12.9800, 77.6000, 45.0
        ));

        // 2. Trigger SOS
        Long employeeId = 555L;
        Violation v1 = sosService.handleSos(trip.getId(), employeeId, pressTime);

        assertNotNull(v1);
        assertEquals(Severity.CRITICAL, v1.getSeverity());
        assertEquals(ViolationType.SOS, v1.getType());
        assertTrue(v1.getEvidence().contains("12.971600"));
        assertTrue(v1.getEvidence().contains("77.594600"));
        assertTrue(v1.getEvidence().contains("fix age: 10s"));

        // 3. Verify routing: both CONTROL_ROOM and TRANSPORT_MANAGER must receive it
        assertEquals(1, controlRoomNotifier.getSendCount(), "CONTROL_ROOM must receive 1 alert");
        assertEquals(1, transportManagerNotifier.getSendCount(), "TRANSPORT_MANAGER must receive 1 alert");

        // The alert message to CONTROL_ROOM must carry the coordinates
        assertNotNull(controlRoomNotifier.getRecordedMessages().getFirst().lat());
        assertEquals(12.9716, controlRoomNotifier.getRecordedMessages().getFirst().lat(), 1e-4);

        // 4. Verify AlertDelivery rows
        List<AlertDelivery> deliveries = alertDeliveryRepository.findByViolationId(v1.getId());
        assertEquals(2, deliveries.size());

        // 5. Exact retry with same employeeId and pressTime: must dedup!
        Violation vRetry = sosService.handleSos(trip.getId(), employeeId, pressTime);
        assertEquals(v1.getId(), vRetry.getId(), "Exact retry must return existing violation");

        // Alerts must NOT be re-sent
        assertEquals(1, controlRoomNotifier.getSendCount(), "No duplicate alerts to CONTROL_ROOM on retry");
        assertEquals(1, transportManagerNotifier.getSendCount(), "No duplicate alerts to TRANSPORT_MANAGER on retry");

        // Database must not have duplicate violations
        List<Violation> allViolations = violationRepository.findByTripIdOrderByDetectedAtAsc(trip.getId());
        assertEquals(1, allViolations.size());
    }

    @Test
    @DisplayName("SOS without prior GPS fix creates CRITICAL violation with unknown location and still alerts")
    void sosWithoutGpsStillAlerts() {
        Driver driver = driverRepository.save(new Driver("DRV-SOS-2", "SOS Driver 2"));
        Vehicle vehicle = vehicleRepository.save(new Vehicle("KA01SOS5678"));
        Trip trip = tripRepository.save(new Trip(driver.getId(), vehicle.getId(), false, Instant.now()));
        stopRepository.save(new Stop(trip.getId(), 1, StopType.PICKUP, 777L, Instant.now(), null));

        Instant pressTime = Instant.parse("2026-10-11T20:30:00Z");
        Long employeeId = 777L;

        Violation v = sosService.handleSos(trip.getId(), employeeId, pressTime);

        assertNotNull(v);
        assertEquals(Severity.CRITICAL, v.getSeverity());
        assertTrue(v.getEvidence().contains("Location: unknown"));

        // Alerts must still be dispatched to both channels
        assertEquals(1, controlRoomNotifier.getSendCount());
        assertEquals(1, transportManagerNotifier.getSendCount());
        assertNull(controlRoomNotifier.getRecordedMessages().getFirst().lat());
    }

    @Test
    @DisplayName("SOS for unknown trip throws NotFoundException")
    void sosUnknownTripThrowsNotFoundException() {
        assertThrows(NotFoundException.class, () ->
                sosService.handleSos(999999L, 101L, Instant.now()));
    }

    @Test
    @DisplayName("SOS for employee with no stop on trip throws ForbiddenTripAccessException")
    void sosEmployeeWithNoStopThrowsForbidden() {
        Driver driver = driverRepository.save(new Driver("DRV-SOS-3", "SOS Driver 3"));
        Vehicle vehicle = vehicleRepository.save(new Vehicle("KA01SOS9999"));
        Trip trip = tripRepository.save(new Trip(driver.getId(), vehicle.getId(), false, Instant.now()));

        assertThrows(ForbiddenTripAccessException.class, () ->
                sosService.handleSos(trip.getId(), 999L, Instant.now()));
    }

    @Test
    @DisplayName("SOS with null pressedAt uses server time and does not throw NPE")
    void sosWithNullPressedAtUsesServerTime() {
        Driver driver = driverRepository.save(new Driver("DRV-SOS-4", "SOS Driver 4"));
        Vehicle vehicle = vehicleRepository.save(new Vehicle("KA01SOS8888"));
        Trip trip = tripRepository.save(new Trip(driver.getId(), vehicle.getId(), false, Instant.now()));
        stopRepository.save(new Stop(trip.getId(), 1, StopType.PICKUP, 444L, Instant.now(), null));

        Violation v = sosService.handleSos(trip.getId(), 444L, null);
        assertNotNull(v);
        assertEquals(Severity.CRITICAL, v.getSeverity());
    }

    @Test
    @DisplayName("SOS retry deduplication does not save a new TripEvent or AuditRun")
    void sosRetryDoesNotSaveNewTripEventOrAuditRun() {
        Driver driver = driverRepository.save(new Driver("DRV-SOS-5", "SOS Driver 5"));
        Vehicle vehicle = vehicleRepository.save(new Vehicle("KA01SOS7777"));
        Trip trip = tripRepository.save(new Trip(driver.getId(), vehicle.getId(), false, Instant.now()));
        stopRepository.save(new Stop(trip.getId(), 1, StopType.PICKUP, 333L, Instant.now(), null));

        Instant pressTime = Instant.parse("2026-10-11T21:00:00Z");
        sosService.handleSos(trip.getId(), 333L, pressTime);

        long eventCountBefore = tripEventRepository.count();
        long runCountBefore = auditRunRepository.count();

        // Retry same SOS
        sosService.handleSos(trip.getId(), 333L, pressTime);

        assertEquals(eventCountBefore, tripEventRepository.count(), "Retry must not save new TripEvent");
        assertEquals(runCountBefore, auditRunRepository.count(), "Retry must not save new AuditRun");
    }
}


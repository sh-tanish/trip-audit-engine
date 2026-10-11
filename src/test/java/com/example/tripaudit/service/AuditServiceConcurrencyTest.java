package com.example.tripaudit.service;

import com.example.tripaudit.alert.NotifierFactory;
import com.example.tripaudit.alert.RecordingNotifier;
import com.example.tripaudit.domain.AlertChannel;
import com.example.tripaudit.domain.AlertDelivery;
import com.example.tripaudit.domain.AuditRun;
import com.example.tripaudit.domain.Driver;
import com.example.tripaudit.domain.Employee;
import com.example.tripaudit.domain.Gender;
import com.example.tripaudit.domain.Stop;
import com.example.tripaudit.domain.StopType;
import com.example.tripaudit.domain.Trip;
import com.example.tripaudit.domain.TripEvent;
import com.example.tripaudit.domain.TripEventType;
import com.example.tripaudit.domain.Vehicle;
import com.example.tripaudit.domain.Violation;
import com.example.tripaudit.repository.AlertDeliveryRepository;
import com.example.tripaudit.repository.DriverRepository;
import com.example.tripaudit.repository.EmployeeRepository;
import com.example.tripaudit.repository.StopRepository;
import com.example.tripaudit.repository.TripEventRepository;
import com.example.tripaudit.repository.TripRepository;
import com.example.tripaudit.repository.VehicleRepository;
import com.example.tripaudit.repository.ViolationRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Concurrency and idempotency integration test for AuditService.
 * Explicitly NOT annotated with @Transactional to test real database transaction boundaries.
 */
@SpringBootTest
class AuditServiceConcurrencyTest {

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
    private TripEventRepository tripEventRepository;

    @Autowired
    private ViolationRepository violationRepository;

    @Autowired
    private AlertDeliveryRepository alertDeliveryRepository;

    @Autowired
    private NotifierFactory notifierFactory;

    private RecordingNotifier transportManagerNotifier;
    private RecordingNotifier controlRoomNotifier;
    private RecordingNotifier dashboardNotifier;

    @BeforeEach
    void setUp() {
        transportManagerNotifier = new RecordingNotifier(AlertChannel.TRANSPORT_MANAGER);
        controlRoomNotifier = new RecordingNotifier(AlertChannel.CONTROL_ROOM);
        dashboardNotifier = new RecordingNotifier(AlertChannel.DASHBOARD);

        notifierFactory.register(transportManagerNotifier);
        notifierFactory.register(controlRoomNotifier);
        notifierFactory.register(dashboardNotifier);
    }

    private Trip setupCompletedTripWithNightViolation(String suffix) {
        Driver driver = driverRepository.save(new Driver("DRV-CC-" + suffix, "Driver " + suffix));
        Vehicle vehicle = vehicleRepository.save(new Vehicle("KA01CC" + suffix));
        Employee woman = employeeRepository.save(new Employee("EMP-CC-W-" + suffix, Gender.FEMALE));

        Trip trip = new Trip(driver.getId(), vehicle.getId(), false, Instant.now());
        trip.complete(Instant.now());
        trip = tripRepository.save(trip);

        // First pickup at 22:30 (night window) without escort -> triggers NIGHT_SAFETY (HIGH)
        Instant pickupTime = Instant.parse("2026-10-11T17:00:00Z"); // 22:30 IST
        Instant dropTime = Instant.parse("2026-10-11T17:30:00Z");   // 23:00 IST

        stopRepository.save(new Stop(trip.getId(), 1, StopType.PICKUP, woman.getId(), pickupTime, null));
        stopRepository.save(new Stop(trip.getId(), 2, StopType.DROP, woman.getId(), dropTime, "1234"));

        // Match OTP at drop so only night safety triggers
        tripEventRepository.save(new TripEvent(
                trip.getId(), TripEventType.OTP_ENTERED, dropTime, 2L, true, null, dropTime
        ));

        // Mark trip completed event
        tripEventRepository.save(new TripEvent(
                trip.getId(), TripEventType.TRIP_COMPLETED, dropTime.plusSeconds(60), null, null, null, dropTime.plusSeconds(60)
        ));

        return trip;
    }

    @Test
    @DisplayName("Auditing the same trip twice gives the same violation set and sends alerts only once")
    void reauditIsIdempotentAndSendsAlertsOnlyOnce() {
        Trip trip = setupCompletedTripWithNightViolation("IDEMPOTENT");

        // First audit
        AuditRun run1 = auditService.auditTrip(trip.getId(), "ADMIN");
        assertTrue(run1.getNewViolations() > 0, "First run must detect new violations");

        List<Violation> violationsRun1 = violationRepository.findByTripIdOrderByDetectedAtAsc(trip.getId());
        assertFalse(violationsRun1.isEmpty());
        int firstAlertCount = transportManagerNotifier.getSendCount();
        assertEquals(violationsRun1.size(), firstAlertCount, "Each HIGH violation must alert transport manager once");
        assertEquals(0, controlRoomNotifier.getSendCount(), "HIGH violation must not alert control room");

        // Second audit (re-audit)
        AuditRun run2 = auditService.auditTrip(trip.getId(), "ADMIN");
        assertEquals(0, run2.getNewViolations(), "Second run must detect 0 new violations");

        List<Violation> violationsRun2 = violationRepository.findByTripIdOrderByDetectedAtAsc(trip.getId());
        assertEquals(violationsRun1.size(), violationsRun2.size(), "Total violation rows must remain identical");
        assertEquals(firstAlertCount, transportManagerNotifier.getSendCount(), "Alert count must NOT increase on re-audit");
    }

    @Test
    @DisplayName("8 concurrent audits create no duplicate violations, no duplicate alert rows, and send alerts exactly once")
    void eightConcurrentAuditsCreateNoDuplicatesAndSendAlertsOnce() throws Exception {
        Trip trip = setupCompletedTripWithNightViolation("CONCURRENT");

        int threadCount = 8;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        List<Future<AuditRun>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            final String worker = "WORKER-" + i;
            futures.add(executor.submit(() -> {
                readyLatch.countDown();
                // Wait for all threads to be ready for synchronized start
                startLatch.await();
                return auditService.auditTrip(trip.getId(), worker);
            }));
        }

        // Release all threads simultaneously
        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown();

        // Ensure all threads complete successfully without throwing exceptions
        int totalNewViolationsReportedByThreads = 0;
        for (Future<AuditRun> future : futures) {
            AuditRun run = future.get(10, TimeUnit.SECONDS);
            assertNotNull(run);
            totalNewViolationsReportedByThreads += run.getNewViolations();
        }
        executor.shutdown();

        // 1. Assert Violation rows in DB
        List<Violation> violationsInDb = violationRepository.findByTripIdOrderByDetectedAtAsc(trip.getId());
        assertFalse(violationsInDb.isEmpty());
        // Exactly matches the number of unique violations detected (NIGHT_SAFETY has 1 or 2 per offending stop)
        assertEquals(violationsInDb.size(), totalNewViolationsReportedByThreads,
                "Across all 8 threads, exactly the unique violations were reported as new");

        // 2. Assert AlertDelivery rows in DB has NO duplicates (one per violation/channel)
        for (Violation v : violationsInDb) {
            List<AlertDelivery> deliveries = alertDeliveryRepository.findByViolationId(v.getId());
            assertEquals(1, deliveries.size(), "Each violation must have exactly one AlertDelivery row per channel");
            assertEquals(AlertChannel.TRANSPORT_MANAGER, deliveries.getFirst().getChannel());
        }

        // 3. Assert recording test Notifier sent each alert exactly once
        assertEquals(violationsInDb.size(), transportManagerNotifier.getSendCount(),
                "Notifier must have received exactly one alert per violation despite 8 concurrent audits");
        assertEquals(0, controlRoomNotifier.getSendCount(),
                "CONTROL_ROOM must receive 0 alerts for HIGH severity violations");

        // 4. Sequential re-audit after concurrency
        AuditRun reauditRun = auditService.auditTrip(trip.getId(), "ADMIN-REAUDIT");
        assertEquals(0, reauditRun.getNewViolations());

        // Violations and deliveries must still have no duplicates
        List<Violation> violationsAfterReaudit = violationRepository.findByTripIdOrderByDetectedAtAsc(trip.getId());
        assertEquals(violationsInDb.size(), violationsAfterReaudit.size());

        // Alert count must remain exactly the same
        assertEquals(violationsInDb.size(), transportManagerNotifier.getSendCount(),
                "Alert count must not change after sequential re-audit");
    }
}

package com.example.tripaudit.service;

import com.example.tripaudit.domain.Driver;
import com.example.tripaudit.domain.Trip;
import com.example.tripaudit.domain.TripStatus;
import com.example.tripaudit.domain.Vehicle;
import com.example.tripaudit.exception.ConflictException;
import com.example.tripaudit.exception.NotFoundException;
import com.example.tripaudit.repository.DriverRepository;
import com.example.tripaudit.repository.TripRepository;
import com.example.tripaudit.repository.VehicleRepository;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.example.tripaudit.config.AuditMetrics;
import com.example.tripaudit.config.AuditProperties;
import com.example.tripaudit.domain.AuditRun;
import com.example.tripaudit.domain.Severity;
import com.example.tripaudit.domain.Stop;
import com.example.tripaudit.domain.StopType;
import com.example.tripaudit.domain.Violation;
import com.example.tripaudit.domain.ViolationType;
import com.example.tripaudit.repository.AuditRunRepository;
import com.example.tripaudit.repository.EmployeeRepository;
import com.example.tripaudit.repository.GpsFixRepository;
import com.example.tripaudit.repository.RouteSegmentRepository;
import com.example.tripaudit.repository.StopRepository;
import com.example.tripaudit.repository.TripEventRepository;
import com.example.tripaudit.repository.ViolationRepository;
import com.example.tripaudit.rules.SafetyRule;
import com.example.tripaudit.alert.AlertDispatcher;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest
class AuditServiceExceptionsTest {

    @Autowired
    private AuditService auditService;

    @Autowired
    private TripRepository tripRepository;

    @Autowired
    private DriverRepository driverRepository;

    @Autowired
    private VehicleRepository vehicleRepository;

    private Long createdTripId;

    @AfterEach
    void tearDown() {
        if (createdTripId != null) {
            tripRepository.deleteById(createdTripId);
        }
    }

    @Test
    @DisplayName("auditTrip throws NotFoundException (404) for unknown trip ID")
    void unknownTripThrowsNotFoundException() {
        long nonExistentId = 999_888_777L;
        NotFoundException ex = assertThrows(NotFoundException.class, () -> {
            auditService.auditTrip(nonExistentId);
        });
        assertTrue(ex.getMessage().contains("Trip not found"));
    }

    @Test
    @DisplayName("auditTrip throws ConflictException (409) when trip is not COMPLETED")
    void nonCompletedTripThrowsConflictException() {
        Driver driver = driverRepository.save(new Driver("DRV-EXC-1", "Exc Driver"));
        Vehicle vehicle = vehicleRepository.save(new Vehicle("KA01EX1234"));

        // Create an ACTIVE trip (not COMPLETED)
        Trip activeTrip = new Trip(driver.getId(), vehicle.getId(), false, Instant.now());
        assertEquals(TripStatus.ACTIVE, activeTrip.getStatus());
        activeTrip = tripRepository.save(activeTrip);
        createdTripId = activeTrip.getId();

        ConflictException ex = assertThrows(ConflictException.class, () -> {
            auditService.auditTrip(createdTripId);
        });
        assertTrue(ex.getMessage().contains("is not COMPLETED"));
    }

    @Test
    @DisplayName("DataIntegrityViolationException is rethrown if violation does not exist by dedupKey")
    void dataIntegrityViolationWithoutDedupKeyRethrows() {
        TripRepository mockTripRepo = mock(TripRepository.class);
        StopRepository mockStopRepo = mock(StopRepository.class);
        RouteSegmentRepository mockRouteRepo = mock(RouteSegmentRepository.class);
        GpsFixRepository mockGpsRepo = mock(GpsFixRepository.class);
        TripEventRepository mockEventRepo = mock(TripEventRepository.class);
        EmployeeRepository mockEmpRepo = mock(EmployeeRepository.class);
        AuditRunRepository mockRunRepo = mock(AuditRunRepository.class);
        ViolationRepository mockViolRepo = mock(ViolationRepository.class);
        ViolationRecorder mockRecorder = mock(ViolationRecorder.class);
        AlertDispatcher mockDispatcher = mock(AlertDispatcher.class);
        SafetyRule mockRule = mock(SafetyRule.class);

        Trip trip = new Trip(1L, 1L, false, Instant.now());
        trip.complete(Instant.now());
        when(mockTripRepo.findById(10L)).thenReturn(Optional.of(trip));
        when(mockStopRepo.findByTripIdOrderBySequenceAsc(10L)).thenReturn(List.of());
        when(mockRouteRepo.findByTripIdOrderBySeqAsc(10L)).thenReturn(List.of());
        when(mockGpsRepo.findByTripIdOrderByRecordedAtAsc(10L)).thenReturn(List.of());
        when(mockEventRepo.findByTripIdOrderByOccurredAtAsc(10L)).thenReturn(List.of());
        when(mockRunRepo.save(any(AuditRun.class))).thenAnswer(inv -> inv.getArgument(0));

        Violation candidate = new Violation(
                10L, ViolationType.NIGHT_SAFETY, Severity.HIGH, "NightRule",
                "stop-1", 1L, 1L, "evidence", Instant.now(), "run-1"
        );
        when(mockRule.evaluate(any())).thenReturn(List.of(candidate));

        doThrow(new DataIntegrityViolationException("Simulated FK failure")).when(mockRecorder).recordIfAbsent(any());
        when(mockViolRepo.existsByDedupKey(candidate.getDedupKey())).thenReturn(false);

        AuditProperties props = new AuditProperties(
                ZoneId.of("Asia/Kolkata"),
                new AuditProperties.Night(LocalTime.of(22, 0), LocalTime.of(6, 0)),
                new AuditProperties.Route(500.0, Duration.ofSeconds(120)),
                new AuditProperties.Speed(Duration.ofSeconds(30), 1.5),
                new AuditProperties.Gps(Duration.ofSeconds(60))
        );

        AuditService service = new AuditService(
                mockTripRepo, mockStopRepo, mockRouteRepo, mockGpsRepo, mockEventRepo,
                mockEmpRepo, mockRunRepo, mockViolRepo, List.of(mockRule), props,
                mockRecorder, mockDispatcher, new AuditMetrics(new SimpleMeterRegistry())
        );

        assertThrows(DataIntegrityViolationException.class, () -> service.auditTrip(10L));
    }

    @Test
    @DisplayName("AuditService queries only the employee ids on the trip's stops, not findAll()")
    void auditQueriesOnlyTripEmployees() {
        TripRepository mockTripRepo = mock(TripRepository.class);
        StopRepository mockStopRepo = mock(StopRepository.class);
        RouteSegmentRepository mockRouteRepo = mock(RouteSegmentRepository.class);
        GpsFixRepository mockGpsRepo = mock(GpsFixRepository.class);
        TripEventRepository mockEventRepo = mock(TripEventRepository.class);
        EmployeeRepository mockEmpRepo = mock(EmployeeRepository.class);
        AuditRunRepository mockRunRepo = mock(AuditRunRepository.class);
        ViolationRepository mockViolRepo = mock(ViolationRepository.class);
        ViolationRecorder mockRecorder = mock(ViolationRecorder.class);
        AlertDispatcher mockDispatcher = mock(AlertDispatcher.class);

        Trip trip = new Trip(1L, 1L, false, Instant.now());
        trip.complete(Instant.now());
        when(mockTripRepo.findById(10L)).thenReturn(Optional.of(trip));

        Stop stop1 = new Stop(10L, 1, StopType.PICKUP, 101L, Instant.now(), null);
        Stop stop2 = new Stop(10L, 2, StopType.DROP, 102L, Instant.now(), "1234");
        when(mockStopRepo.findByTripIdOrderBySequenceAsc(10L)).thenReturn(List.of(stop1, stop2));

        when(mockRouteRepo.findByTripIdOrderBySeqAsc(10L)).thenReturn(List.of());
        when(mockGpsRepo.findByTripIdOrderByRecordedAtAsc(10L)).thenReturn(List.of());
        when(mockEventRepo.findByTripIdOrderByOccurredAtAsc(10L)).thenReturn(List.of());
        when(mockEmpRepo.findAllById(any())).thenReturn(List.of());
        when(mockRunRepo.save(any(AuditRun.class))).thenAnswer(inv -> inv.getArgument(0));

        AuditProperties props = new AuditProperties(
                ZoneId.of("Asia/Kolkata"),
                new AuditProperties.Night(LocalTime.of(22, 0), LocalTime.of(6, 0)),
                new AuditProperties.Route(500.0, Duration.ofSeconds(120)),
                new AuditProperties.Speed(Duration.ofSeconds(30), 1.5),
                new AuditProperties.Gps(Duration.ofSeconds(60))
        );

        AuditService service = new AuditService(
                mockTripRepo, mockStopRepo, mockRouteRepo, mockGpsRepo, mockEventRepo,
                mockEmpRepo, mockRunRepo, mockViolRepo, List.of(), props,
                mockRecorder, mockDispatcher, new AuditMetrics(new SimpleMeterRegistry())
        );

        service.auditTrip(10L);

        verify(mockEmpRepo, times(1)).findAllById(argThat(ids -> {
            List<Long> list = (List<Long>) ids;
            return list.contains(101L) && list.contains(102L) && list.size() == 2;
        }));
        verify(mockEmpRepo, never()).findAll();
    }
}

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

import static org.junit.jupiter.api.Assertions.*;

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
}

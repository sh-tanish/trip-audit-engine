package com.example.tripaudit.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "trip")
public class Trip {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "driver_id", nullable = false)
    private Long driverId;

    @Column(name = "vehicle_id", nullable = false)
    private Long vehicleId;

    // Value at creation. Later escort changes are TRIP_EVENTs, not edits to this column.
    @Column(name = "escort_assigned", nullable = false)
    private boolean escortAssigned;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TripStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected Trip() { } // required by JPA

    public Trip(Long driverId, Long vehicleId, boolean escortAssigned, Instant createdAt) {
        this.driverId = driverId;
        this.vehicleId = vehicleId;
        this.escortAssigned = escortAssigned;
        this.createdAt = createdAt;
        this.status = TripStatus.ACTIVE;
    }

    /** Operational state: a trip goes ACTIVE -> COMPLETED exactly once. */
    public void complete(Instant completedAt) {
        this.status = TripStatus.COMPLETED;
        this.completedAt = completedAt;
    }

    public Long getId() { return id; }
    public Long getDriverId() { return driverId; }
    public Long getVehicleId() { return vehicleId; }
    public boolean isEscortAssigned() { return escortAssigned; }
    public TripStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getCompletedAt() { return completedAt; }
}

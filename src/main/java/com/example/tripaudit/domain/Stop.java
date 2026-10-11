package com.example.tripaudit.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

@Entity
@Table(name = "stop",
        uniqueConstraints = @UniqueConstraint(name = "uk_stop_trip_sequence", columnNames = {"trip_id", "sequence"}))
public class Stop {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "trip_id", nullable = false)
    private Long tripId;

    // 1..n, unique per trip (see the table constraint above).
    @Column(name = "sequence", nullable = false)
    private int sequence;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StopType type;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(name = "planned_time", nullable = false)
    private Instant plannedTime;

    // DROP only. Never returned in API responses and never logged.
    @Column(name = "expected_otp")
    private String expectedOtp;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StopStatus status;

    protected Stop() { } // required by JPA

    public Stop(Long tripId, int sequence, StopType type, Long employeeId, Instant plannedTime, String expectedOtp) {
        this.tripId = tripId;
        this.sequence = sequence;
        this.type = type;
        this.employeeId = employeeId;
        this.plannedTime = plannedTime;
        this.expectedOtp = expectedOtp;
        this.status = StopStatus.PENDING;
    }

    public void setStatus(StopStatus status) { this.status = status; }

    public Long getId() { return id; }
    public Long getTripId() { return tripId; }
    public int getSequence() { return sequence; }
    public StopType getType() { return type; }
    public Long getEmployeeId() { return employeeId; }
    public Instant getPlannedTime() { return plannedTime; }
    public String getExpectedOtp() { return expectedOtp; }
    public StopStatus getStatus() { return status; }
}

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

/** A raw event about a trip. Append-only. The OTP the driver typed is deliberately NOT stored. */
@Entity
@Table(name = "trip_event")
public class TripEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "trip_id", nullable = false)
    private Long tripId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TripEventType type;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    // OTP_ENTERED only.
    @Column(name = "stop_id")
    private Long stopId;

    // OTP_ENTERED only. Boxed Boolean so "not an OTP event" can be null.
    @Column(name = "otp_matched")
    private Boolean otpMatched;

    // SOS only (opaque employee id).
    @Column(name = "employee_id")
    private Long employeeId;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    protected TripEvent() { } // required by JPA

    public TripEvent(Long tripId, TripEventType type, Instant occurredAt, Long stopId,
                     Boolean otpMatched, Long employeeId, Instant receivedAt) {
        this.tripId = tripId;
        this.type = type;
        this.occurredAt = occurredAt;
        this.stopId = stopId;
        this.otpMatched = otpMatched;
        this.employeeId = employeeId;
        this.receivedAt = receivedAt;
    }

    public Long getId() { return id; }
    public Long getTripId() { return tripId; }
    public TripEventType getType() { return type; }
    public Instant getOccurredAt() { return occurredAt; }
    public Long getStopId() { return stopId; }
    public Boolean getOtpMatched() { return otpMatched; }
    public Long getEmployeeId() { return employeeId; }
    public Instant getReceivedAt() { return receivedAt; }
}

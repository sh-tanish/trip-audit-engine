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

/**
 * One notification attempt for one violation on one channel. Kept separate from Violation so a failing
 * notifier can never roll back or lose the violation.
 */
@Entity
@Table(name = "alert_delivery",
        uniqueConstraints = @UniqueConstraint(name = "uk_alert_delivery_violation_channel",
                columnNames = {"violation_id", "channel"}))
public class AlertDelivery {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "violation_id", nullable = false)
    private Long violationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AlertChannel channel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DeliveryStatus status;

    @Column(nullable = false)
    private int attempts;

    // Sanitised message only: never an OTP, phone number or coordinates.
    @Column(name = "last_error")
    private String lastError;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AlertDelivery() { } // required by JPA

    public AlertDelivery(Long violationId, AlertChannel channel, Instant now) {
        this.violationId = violationId;
        this.channel = channel;
        this.status = DeliveryStatus.PENDING;
        this.attempts = 0;
        this.updatedAt = now;
    }

    public void markSent(Instant now) {
        this.attempts++;
        this.status = DeliveryStatus.SENT;
        this.lastError = null;
        this.updatedAt = now;
    }

    public void markFailed(String sanitisedError, Instant now) {
        this.attempts++;
        this.status = DeliveryStatus.FAILED;
        this.lastError = sanitisedError;
        this.updatedAt = now;
    }

    public Long getId() { return id; }
    public Long getViolationId() { return violationId; }
    public AlertChannel getChannel() { return channel; }
    public DeliveryStatus getStatus() { return status; }
    public int getAttempts() { return attempts; }
    public String getLastError() { return lastError; }
    public Instant getUpdatedAt() { return updatedAt; }
}

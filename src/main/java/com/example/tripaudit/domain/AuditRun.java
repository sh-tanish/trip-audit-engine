package com.example.tripaudit.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** One execution of the audit (or one SOS press). Answers "who triggered it and when". */
@Entity
@Table(name = "audit_run")
public class AuditRun {

    // UUID string, assigned in the constructor, so a Violation can reference it before anything else happens.
    @Id
    private String id;

    @Column(name = "trip_id", nullable = false)
    private Long tripId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AuditRunKind kind;

    @Column(name = "triggered_by", nullable = false)
    private String triggeredBy;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "new_violations", nullable = false)
    private int newViolations;

    protected AuditRun() { } // required by JPA

    public AuditRun(Long tripId, AuditRunKind kind, String triggeredBy, Instant startedAt) {
        this.id = UUID.randomUUID().toString();
        this.tripId = tripId;
        this.kind = kind;
        this.triggeredBy = triggeredBy;
        this.startedAt = startedAt;
    }

    /** Operational state: filled in once the run is over. */
    public void finish(Instant finishedAt, int newViolations) {
        this.finishedAt = finishedAt;
        this.newViolations = newViolations;
    }

    public String getId() { return id; }
    public Long getTripId() { return tripId; }
    public AuditRunKind getKind() { return kind; }
    public String getTriggeredBy() { return triggeredBy; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public int getNewViolations() { return newViolations; }
}

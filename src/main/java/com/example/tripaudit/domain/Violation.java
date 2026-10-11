package com.example.tripaudit.domain;

import com.example.tripaudit.audit.DedupKeys;
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
import org.hibernate.annotations.Immutable;

/**
 * The output of a rule. Append-only: there are no setters, every column is updatable=false, and
 * Hibernate's @Immutable makes it ignore any change made to a loaded instance.
 * (Someone with direct DB access could still edit rows; this is application-level protection.)
 */
@Entity
@Immutable
@Table(name = "violation",
        uniqueConstraints = @UniqueConstraint(name = "uk_violation_dedup_key", columnNames = "dedup_key"))
public class Violation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // sha256(tripId | type | subjectKey). UNIQUE: this is what makes re-auditing idempotent.
    @Column(name = "dedup_key", nullable = false, updatable = false, length = 64)
    private String dedupKey;

    @Column(name = "trip_id", nullable = false, updatable = false)
    private Long tripId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private ViolationType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private Severity severity;

    @Column(name = "rule_name", nullable = false, updatable = false)
    private String ruleName;

    // Stable across re-runs: stopId, run start epoch-ms, or employeeId:pressedAt.
    @Column(name = "subject_key", nullable = false, updatable = false)
    private String subjectKey;

    @Column(name = "stop_id", updatable = false)
    private Long stopId;

    // Opaque id only, never a name.
    @Column(name = "employee_id", updatable = false)
    private Long employeeId;

    // Short summary with no personal data.
    @Column(nullable = false, updatable = false, length = 1000)
    private String evidence;

    @Column(name = "detected_at", nullable = false, updatable = false)
    private Instant detectedAt;

    @Column(name = "audit_run_id", nullable = false, updatable = false)
    private String auditRunId;

    protected Violation() { } // required by JPA

    /** Rules call this. The dedupKey is derived here so no rule can forget it or get it wrong. */
    public Violation(Long tripId, ViolationType type, Severity severity, String ruleName, String subjectKey,
                     Long stopId, Long employeeId, String evidence, Instant detectedAt, String auditRunId) {
        this.tripId = tripId;
        this.type = type;
        this.severity = severity;
        this.ruleName = ruleName;
        this.subjectKey = subjectKey;
        this.stopId = stopId;
        this.employeeId = employeeId;
        this.evidence = evidence;
        this.detectedAt = detectedAt;
        this.auditRunId = auditRunId;
        this.dedupKey = DedupKeys.of(tripId, type.name(), subjectKey);
    }

    public Long getId() { return id; }
    public String getDedupKey() { return dedupKey; }
    public Long getTripId() { return tripId; }
    public ViolationType getType() { return type; }
    public Severity getSeverity() { return severity; }
    public String getRuleName() { return ruleName; }
    public String getSubjectKey() { return subjectKey; }
    public Long getStopId() { return stopId; }
    public Long getEmployeeId() { return employeeId; }
    public String getEvidence() { return evidence; }
    public Instant getDetectedAt() { return detectedAt; }
    public String getAuditRunId() { return auditRunId; }
}

package com.example.tripaudit.config;

import com.example.tripaudit.domain.Severity;
import com.example.tripaudit.domain.ViolationType;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * Encapsulates all Micrometer application metrics for safety audits and alerts.
 * Exposed via Spring Boot Actuator (/actuator/metrics).
 */
@Component
public class AuditMetrics {

    private final MeterRegistry meterRegistry;
    private final Counter auditsTotal;
    private final Counter alertsFailedTotal;
    private final Counter sosTotal;

    public AuditMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.auditsTotal = Counter.builder("audits_total")
                .description("Total number of completed audit runs")
                .register(meterRegistry);
        this.alertsFailedTotal = Counter.builder("alerts_failed_total")
                .description("Total number of failed alert notifications")
                .register(meterRegistry);
        this.sosTotal = Counter.builder("sos_total")
                .description("Total number of SOS emergency alerts raised")
                .register(meterRegistry);
    }

    /**
     * Increments the violations_total counter tagged with violation type and severity.
     */
    public void incrementViolation(ViolationType type, Severity severity) {
        Counter.builder("violations_total")
                .description("Total number of newly detected safety violations")
                .tag("type", type.name())
                .tag("severity", severity.name())
                .register(meterRegistry)
                .increment();
    }

    public void incrementAudits() {
        auditsTotal.increment();
    }

    public void incrementAlertsFailed() {
        alertsFailedTotal.increment();
    }

    public void incrementSos() {
        sosTotal.increment();
    }

    public MeterRegistry getMeterRegistry() {
        return meterRegistry;
    }
}

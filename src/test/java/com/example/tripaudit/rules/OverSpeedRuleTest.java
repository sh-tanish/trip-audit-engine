package com.example.tripaudit.rules;

import com.example.tripaudit.config.AuditProperties;
import com.example.tripaudit.domain.GeoPoint;
import com.example.tripaudit.domain.GpsFix;
import com.example.tripaudit.domain.RouteSegment;
import com.example.tripaudit.domain.Severity;
import com.example.tripaudit.domain.Violation;
import com.example.tripaudit.domain.ViolationType;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OverSpeedRuleTest {

    private final OverSpeedRule rule = new OverSpeedRule();
    private final AuditProperties config = TripContextFactory.defaultConfig();
    private final Instant detectedAt = Instant.parse("2026-10-11T20:00:00Z");

    // Route segment with 50 km/h speed limit
    private final RouteSegment segment0 = TestFixtures.segment(
            1L, 200L, 0,
            new GeoPoint(12.9716, 77.5900),
            new GeoPoint(12.9716, 77.6500),
            50.0
    );

    @Test
    @DisplayName("Sustained over-speed within 1.5x limit => OVER_SPEED (MEDIUM)")
    void sustainedModerateOverSpeedTriggersMediumViolation() {
        Long tripId = 200L;
        Instant start = Instant.parse("2026-10-11T12:00:00Z");

        // 3 fixes 30s apart: 0s, 30s, 60s (duration 60s >= 30s) at 60 km/h in 50 km/h zone
        // 60 / 50 = 1.2 <= 1.5
        List<GpsFix> fixes = List.of(
                new GpsFix(tripId, start, 12.9716, 77.5950, 60.0),
                new GpsFix(tripId, start.plusSeconds(30), 12.9716, 77.6000, 60.0),
                new GpsFix(tripId, start.plusSeconds(60), 12.9716, 77.6050, 60.0)
        );

        TripContext ctx = TripContextFactory.create(
                tripId, "run-1", detectedAt, false,
                List.of(), List.of(), List.of(segment0), fixes, List.of(), config
        );

        List<Violation> violations = rule.evaluate(ctx);
        assertThat(violations).hasSize(1);
        Violation v = violations.get(0);
        assertThat(v.getType()).isEqualTo(ViolationType.OVER_SPEED);
        assertThat(v.getSeverity()).isEqualTo(Severity.MEDIUM);
        assertThat(v.getEvidence()).contains("peak 60.0 km/h in 50.0 km/h zone");
        assertThat(v.getEvidence()).contains("segment 0");
    }

    @Test
    @DisplayName("A single speed spike => no violation")
    void singleSpeedSpikeGivesNoViolation() {
        Long tripId = 201L;
        Instant start = Instant.parse("2026-10-11T12:00:00Z");

        // Single ping at 120 km/h, preceded and followed by legal speeds
        List<GpsFix> fixes = List.of(
                new GpsFix(tripId, start, 12.9716, 77.5950, 45.0),
                new GpsFix(tripId, start.plusSeconds(30), 12.9716, 77.6000, 120.0),
                new GpsFix(tripId, start.plusSeconds(60), 12.9716, 77.6050, 45.0)
        );

        TripContext ctx = TripContextFactory.create(
                tripId, "run-1", detectedAt, false,
                List.of(), List.of(), List.of(segment0), fixes, List.of(), config
        );

        List<Violation> violations = rule.evaluate(ctx);
        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("Extreme over-speed (peak > 1.5x limit) => OVER_SPEED (CRITICAL)")
    void extremeOverSpeedTriggersCriticalViolation() {
        Long tripId = 202L;
        Instant start = Instant.parse("2026-10-11T12:00:00Z");

        // 80 km/h in 50 km/h zone: ratio = 1.6 > 1.5
        // Duration 30s (0s and 30s)
        List<GpsFix> fixes = List.of(
                new GpsFix(tripId, start, 12.9716, 77.5950, 80.0),
                new GpsFix(tripId, start.plusSeconds(30), 12.9716, 77.6000, 80.0)
        );

        TripContext ctx = TripContextFactory.create(
                tripId, "run-1", detectedAt, false,
                List.of(), List.of(), List.of(segment0), fixes, List.of(), config
        );

        List<Violation> violations = rule.evaluate(ctx);
        assertThat(violations).hasSize(1);
        Violation v = violations.get(0);
        assertThat(v.getType()).isEqualTo(ViolationType.OVER_SPEED);
        assertThat(v.getSeverity()).isEqualTo(Severity.CRITICAL);
        assertThat(v.getEvidence()).contains("peak 80.0 km/h in 50.0 km/h zone");
    }

    @Test
    @DisplayName("2 fast fixes 30 s apart = OVER_SPEED, 1 fast fix = none")
    void twoFixesAtMinDurationVsOneFix() {
        Long tripId = 203L;
        Instant start = Instant.parse("2026-10-11T12:00:00Z");

        // 1 fast fix: duration 0 < 30s => none
        TripContext ctxOne = TripContextFactory.create(
                tripId, "run-1", detectedAt, false,
                List.of(), List.of(), List.of(segment0),
                List.of(new GpsFix(tripId, start, 12.9716, 77.5950, 65.0)),
                List.of(), config
        );
        assertThat(rule.evaluate(ctxOne)).isEmpty();

        // 2 fast fixes 30s apart: duration 30s >= 30s => violation
        TripContext ctxTwo = TripContextFactory.create(
                tripId, "run-1", detectedAt, false,
                List.of(), List.of(), List.of(segment0),
                List.of(
                        new GpsFix(tripId, start, 12.9716, 77.5950, 65.0),
                        new GpsFix(tripId, start.plusSeconds(30), 12.9716, 77.6000, 65.0)
                ),
                List.of(), config
        );
        assertThat(rule.evaluate(ctxTwo)).hasSize(1);
    }

    @Test
    @DisplayName("Empty GPS list => no crash")
    void emptyGpsListGivesNoCrash() {
        Long tripId = 204L;
        TripContext ctx = TripContextFactory.create(
                tripId, "run-1", detectedAt, false,
                List.of(), List.of(), List.of(segment0), List.of(), List.of(), config
        );

        assertThat(rule.evaluate(ctx)).isEmpty();
    }

    @Test
    @DisplayName("Speed equal to limit => no violation")
    void speedEqualToLimitGivesNoViolation() {
        Long tripId = 205L;
        Instant start = Instant.parse("2026-10-11T12:00:00Z");

        // 3 fixes at exactly 50.0 km/h in 50 km/h zone
        List<GpsFix> fixes = List.of(
                new GpsFix(tripId, start, 12.9716, 77.5950, 50.0),
                new GpsFix(tripId, start.plusSeconds(30), 12.9716, 77.6000, 50.0),
                new GpsFix(tripId, start.plusSeconds(60), 12.9716, 77.6050, 50.0)
        );

        TripContext ctx = TripContextFactory.create(
                tripId, "run-1", detectedAt, false,
                List.of(), List.of(), List.of(segment0), fixes, List.of(), config
        );

        List<Violation> violations = rule.evaluate(ctx);
        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("Speed ratio exactly 1.5x limit => MEDIUM severity (not CRITICAL)")
    void speedRatioExactlyOnePointFiveGivesMedium() {
        Long tripId = 206L;
        Instant start = Instant.parse("2026-10-11T12:00:00Z");

        // 75.0 km/h in 50.0 km/h zone gives ratio = 1.5 exactly
        List<GpsFix> fixes = List.of(
                new GpsFix(tripId, start, 12.9716, 77.5950, 75.0),
                new GpsFix(tripId, start.plusSeconds(30), 12.9716, 77.6000, 75.0)
        );

        TripContext ctx = TripContextFactory.create(
                tripId, "run-1", detectedAt, false,
                List.of(), List.of(), List.of(segment0), fixes, List.of(), config
        );

        List<Violation> violations = rule.evaluate(ctx);
        assertThat(violations).hasSize(1);
        assertThat(violations.get(0).getSeverity()).isEqualTo(Severity.MEDIUM);
    }

    @Test
    @DisplayName("Speeding across two segments with different limits => single continuous run violation")
    void speedingAcrossTwoSegmentsWithDifferentLimits() {
        Long tripId = 207L;
        Instant start = Instant.parse("2026-10-11T12:00:00Z");

        RouteSegment seg0 = TestFixtures.segment(
                1L, tripId, 0,
                new GeoPoint(12.9716, 77.5900),
                new GeoPoint(12.9716, 77.6000),
                40.0 // 40 km/h limit
        );
        RouteSegment seg1 = TestFixtures.segment(
                2L, tripId, 1,
                new GeoPoint(12.9716, 77.6000),
                new GeoPoint(12.9716, 77.6100),
                60.0 // 60 km/h limit
        );

        // Fix 1 on seg0 at 50 km/h (> 40), Fix 2 on seg1 at 70 km/h (> 60)
        List<GpsFix> fixes = List.of(
                new GpsFix(tripId, start, 12.9716, 77.5950, 50.0),
                new GpsFix(tripId, start.plusSeconds(30), 12.9716, 77.6050, 70.0)
        );

        TripContext ctx = TripContextFactory.create(
                tripId, "run-1", detectedAt, false,
                List.of(), List.of(), List.of(seg0, seg1), fixes, List.of(), config
        );

        List<Violation> violations = rule.evaluate(ctx);
        assertThat(violations).hasSize(1);
        assertThat(violations.get(0).getType()).isEqualTo(ViolationType.OVER_SPEED);
    }
}

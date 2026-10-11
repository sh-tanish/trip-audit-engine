package com.example.tripaudit.rules;

import com.example.tripaudit.config.AuditProperties;
import com.example.tripaudit.detect.GeoMath;
import com.example.tripaudit.detect.SegmentMatch;
import com.example.tripaudit.domain.GeoPoint;
import com.example.tripaudit.domain.GpsFix;
import com.example.tripaudit.domain.RouteSegment;
import com.example.tripaudit.domain.Severity;
import com.example.tripaudit.domain.Violation;
import com.example.tripaudit.domain.ViolationType;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RouteDeviationRuleTest {

    private final RouteDeviationRule rule = new RouteDeviationRule();
    private final AuditProperties config = TripContextFactory.defaultConfig();
    private final Instant detectedAt = Instant.parse("2026-10-11T20:00:00Z");

    // Route segment along latitude 12.9716 from lon 77.5900 to 77.6500 (~6.5 km)
    private final RouteSegment segment0 = TestFixtures.segment(
            1L, 100L, 0,
            new GeoPoint(12.9716, 77.5900),
            new GeoPoint(12.9716, 77.6500),
            50.0
    );

    @Test
    @DisplayName("800 m off route for 4 minutes => ROUTE_DEVIATION (HIGH) with segment info")
    void eightHundredMetersOffForFourMinutesTriggersViolation() {
        Long tripId = 100L;
        Instant start = Instant.parse("2026-10-11T12:00:00Z");

        // 1 deg latitude is ~111 km, so 0.0072 deg latitude is ~800 m off
        // 9 fixes every 30 s: 0s, 30s, 60s, 90s, 120s, 150s, 180s, 210s, 240s (4 minutes)
        List<GpsFix> fixes = new ArrayList<>();
        for (int i = 0; i <= 8; i++) {
            fixes.add(new GpsFix(
                    tripId,
                    start.plusSeconds(i * 30L),
                    12.9716 + 0.0072, // ~800m north of route
                    77.6000 + (i * 0.001),
                    40.0
            ));
        }

        TripContext ctx = TripContextFactory.create(
                tripId, "run-1", detectedAt, false,
                List.of(), List.of(), List.of(segment0), fixes, List.of(), config
        );

        List<Violation> violations = rule.evaluate(ctx);
        assertThat(violations).hasSize(1);
        Violation v = violations.get(0);
        assertThat(v.getType()).isEqualTo(ViolationType.ROUTE_DEVIATION);
        assertThat(v.getSeverity()).isEqualTo(Severity.HIGH);
        assertThat(v.getEvidence()).contains("segment 0");
        assertThat(v.getEvidence()).contains("duration 240s");
        assertThat(v.getEvidence()).contains("max");
        assertThat(v.getSubjectKey()).isEqualTo(String.valueOf(start.toEpochMilli()));
    }

    @Test
    @DisplayName("A single ping 800 m off => no violation (single-ping blip protection)")
    void singlePingBlipGivesNoViolation() {
        Long tripId = 101L;
        Instant start = Instant.parse("2026-10-11T12:00:00Z");

        List<GpsFix> fixes = List.of(
                new GpsFix(tripId, start, 12.9716, 77.6000, 40.0), // on route
                new GpsFix(tripId, start.plusSeconds(30), 12.9716 + 0.0072, 77.6050, 40.0), // single 800m spike
                new GpsFix(tripId, start.plusSeconds(60), 12.9716, 77.6100, 40.0) // back on route
        );

        TripContext ctx = TripContextFactory.create(
                tripId, "run-1", detectedAt, false,
                List.of(), List.of(), List.of(segment0), fixes, List.of(), config
        );

        List<Violation> violations = rule.evaluate(ctx);
        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("GPS run boundaries: 4 fixes 30 s apart = 90 s (< 120s) => no violation")
    void fourFixesBelowMinDurationGivesNoViolation() {
        Long tripId = 102L;
        Instant start = Instant.parse("2026-10-11T12:00:00Z");

        // 4 fixes: 0s, 30s, 60s, 90s -> duration is 90 seconds (less than 120 s minDuration)
        List<GpsFix> fixes = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            fixes.add(new GpsFix(tripId, start.plusSeconds(i * 30L), 12.9716 + 0.0072, 77.6000, 40.0));
        }

        TripContext ctx = TripContextFactory.create(
                tripId, "run-1", detectedAt, false,
                List.of(), List.of(), List.of(segment0), fixes, List.of(), config
        );

        List<Violation> violations = rule.evaluate(ctx);
        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("GPS run boundaries: 5 fixes 30 s apart = 120 s (>= 120s) => violation")
    void fiveFixesAtMinDurationTriggersViolation() {
        Long tripId = 103L;
        Instant start = Instant.parse("2026-10-11T12:00:00Z");

        // 5 fixes: 0s, 30s, 60s, 90s, 120s -> duration is exactly 120 seconds
        List<GpsFix> fixes = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            fixes.add(new GpsFix(tripId, start.plusSeconds(i * 30L), 12.9716 + 0.0072, 77.6000, 40.0));
        }

        TripContext ctx = TripContextFactory.create(
                tripId, "run-1", detectedAt, false,
                List.of(), List.of(), List.of(segment0), fixes, List.of(), config
        );

        List<Violation> violations = rule.evaluate(ctx);
        assertThat(violations).hasSize(1);
        assertThat(violations.get(0).getType()).isEqualTo(ViolationType.ROUTE_DEVIATION);
    }

    @Test
    @DisplayName("Empty GPS list => no crash, returns empty list")
    void emptyGpsListGivesNoCrash() {
        Long tripId = 104L;
        TripContext ctx = TripContextFactory.create(
                tripId, "run-1", detectedAt, false,
                List.of(), List.of(), List.of(segment0), List.of(), List.of(), config
        );

        List<Violation> violations = rule.evaluate(ctx);
        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("Distance exactly 500 m off route => no deviation")
    void distanceExactlyFiveHundredMetersGivesNoDeviation() {
        Long tripId = 105L;
        Instant start = Instant.parse("2026-10-11T12:00:00Z");

        // 5 matched fixes 30s apart (duration 120s >= 120s minDuration) with distance exactly 500.0 m (threshold)
        List<MatchedFix> matchedFixes = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            GpsFix fix = new GpsFix(tripId, start.plusSeconds(i * 30L), 12.9716, 77.6000 + (i * 0.001), 40.0);
            matchedFixes.add(new MatchedFix(fix, new SegmentMatch(0, 500.0)));
        }

        TripContext ctx = new TripContext(
                tripId, "run-1", detectedAt, false,
                List.of(), java.util.Map.of(), List.of(segment0), matchedFixes, List.of(), config
        );

        List<Violation> violations = rule.evaluate(ctx);
        assertThat(violations).isEmpty();
    }
}

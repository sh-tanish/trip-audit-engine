package com.example.tripaudit.rules;

import com.example.tripaudit.config.AuditProperties;
import com.example.tripaudit.domain.Employee;
import com.example.tripaudit.domain.Gender;
import com.example.tripaudit.domain.GeoPoint;
import com.example.tripaudit.domain.GpsFix;
import com.example.tripaudit.domain.RouteSegment;
import com.example.tripaudit.domain.Stop;
import com.example.tripaudit.domain.StopType;
import com.example.tripaudit.domain.TripEvent;
import com.example.tripaudit.domain.Violation;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SafetyRulesAcceptanceTest {

    private final List<SafetyRule> allRules = List.of(
            new NightSafetyRule(),
            new OtpAtDropRule(),
            new RouteDeviationRule(),
            new OverSpeedRule()
    );

    private final AuditProperties config = TripContextFactory.defaultConfig();
    private final Instant detectedAt = Instant.parse("2026-10-11T20:00:00Z");

    @Test
    @DisplayName("Evaluating the same context twice gives identical dedupKeys across all rules")
    void evaluatingSameContextTwiceProducesIdenticalDedupKeys() {
        Long tripId = 500L;
        Instant dropTime = Instant.parse("2026-10-11T17:40:00Z"); // 23:10 IST
        Instant pickupTime = dropTime.minusSeconds(1800);

        Employee woman = TestFixtures.employee(1L, "EMP-101", Gender.FEMALE);
        Stop pickup = TestFixtures.stop(101L, tripId, 1, StopType.PICKUP, woman.getId(), pickupTime, null);
        Stop drop = TestFixtures.stop(102L, tripId, 2, StopType.DROP, woman.getId(), dropTime, "1234");

        RouteSegment seg0 = TestFixtures.segment(1L, tripId, 0,
                new GeoPoint(12.9716, 77.5900), new GeoPoint(12.9716, 77.6500), 50.0);

        // 5 fixes with 80 km/h speed (>1.5x) and on route
        List<GpsFix> fixes = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            fixes.add(new GpsFix(tripId, dropTime.plusSeconds(i * 30L), 12.9716, 77.6000 + i * 0.001, 80.0));
        }

        // OTP mismatch event
        TripEvent failedOtp = TestFixtures.otpEvent(1L, tripId, drop.getId(), false, dropTime.plusSeconds(10));

        TripContext ctx = TripContextFactory.create(
                tripId, "run-1", detectedAt, false,
                List.of(pickup, drop), List.of(woman), List.of(seg0), fixes, List.of(failedOtp), config
        );

        // Run 1
        List<Violation> firstRun = new ArrayList<>();
        for (SafetyRule rule : allRules) {
            firstRun.addAll(rule.evaluate(ctx));
        }

        // Run 2 (different runId and detectedAt simulated, but same core trip facts)
        TripContext ctx2 = TripContextFactory.create(
                tripId, "run-2", detectedAt.plusSeconds(60), false,
                List.of(pickup, drop), List.of(woman), List.of(seg0), fixes, List.of(failedOtp), config
        );

        List<Violation> secondRun = new ArrayList<>();
        for (SafetyRule rule : allRules) {
            secondRun.addAll(rule.evaluate(ctx2));
        }

        assertThat(firstRun).isNotEmpty();
        assertThat(firstRun).hasSameSizeAs(secondRun);

        List<String> firstKeys = firstRun.stream().map(Violation::getDedupKey).toList();
        List<String> secondKeys = secondRun.stream().map(Violation::getDedupKey).toList();

        assertThat(firstKeys).containsExactlyElementsOf(secondKeys);

        // Evidence across all rules must never contain PII or OTP values
        for (Violation v : firstRun) {
            assertThat(v.getEvidence()).doesNotContain("1234");
            assertThat(v.getEvidence()).doesNotContain("EMP-101");
        }
    }
}

package com.example.tripaudit.rules;

import com.example.tripaudit.config.AuditProperties;
import com.example.tripaudit.domain.Employee;
import com.example.tripaudit.domain.Gender;
import com.example.tripaudit.domain.Severity;
import com.example.tripaudit.domain.Stop;
import com.example.tripaudit.domain.StopType;
import com.example.tripaudit.domain.TripEvent;
import com.example.tripaudit.domain.Violation;
import com.example.tripaudit.domain.ViolationType;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OtpAtDropRuleTest {

    private final OtpAtDropRule rule = new OtpAtDropRule();
    private final AuditProperties config = TripContextFactory.defaultConfig();
    private final Instant detectedAt = Instant.parse("2026-10-11T20:00:00Z");

    @Test
    @DisplayName("OTP mismatch => OTP_MISMATCH (HIGH)")
    void otpMismatchTriggersViolation() {
        Long tripId = 20L;
        Instant now = Instant.parse("2026-10-11T12:00:00Z");

        Employee emp = TestFixtures.employee(1L, "EMP-101", Gender.MALE);
        Stop pickup = TestFixtures.stop(1L, tripId, 1, StopType.PICKUP, emp.getId(), now, null);
        Stop drop = TestFixtures.stop(2L, tripId, 2, StopType.DROP, emp.getId(), now.plusSeconds(600), "9999");

        TripEvent failedOtp = TestFixtures.otpEvent(10L, tripId, drop.getId(), false, now.plusSeconds(610));

        TripContext ctx = TripContextFactory.create(
                tripId, "run-1", detectedAt, false,
                List.of(pickup, drop), List.of(emp), List.of(), List.of(), List.of(failedOtp), config
        );

        List<Violation> violations = rule.evaluate(ctx);
        assertThat(violations).hasSize(1);
        Violation v = violations.get(0);
        assertThat(v.getType()).isEqualTo(ViolationType.OTP_MISMATCH);
        assertThat(v.getSeverity()).isEqualTo(Severity.HIGH);
        assertThat(v.getStopId()).isEqualTo(drop.getId());
        assertThat(v.getEvidence()).contains("OTP verification failed (MISMATCH)");
        assertThat(v.getEvidence()).doesNotContain("9999");
    }

    @Test
    @DisplayName("Matching OTP => no violation")
    void matchingOtpGivesNoViolation() {
        Long tripId = 21L;
        Instant now = Instant.parse("2026-10-11T12:00:00Z");

        Employee emp = TestFixtures.employee(1L, "EMP-101", Gender.MALE);
        Stop pickup = TestFixtures.stop(1L, tripId, 1, StopType.PICKUP, emp.getId(), now, null);
        Stop drop = TestFixtures.stop(2L, tripId, 2, StopType.DROP, emp.getId(), now.plusSeconds(600), "9999");

        TripEvent matchedOtp = TestFixtures.otpEvent(11L, tripId, drop.getId(), true, now.plusSeconds(605));

        TripContext ctx = TripContextFactory.create(
                tripId, "run-1", detectedAt, false,
                List.of(pickup, drop), List.of(emp), List.of(), List.of(), List.of(matchedOtp), config
        );

        List<Violation> violations = rule.evaluate(ctx);
        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("Missing OTP on a completed drop => flagged as OTP_MISMATCH (HIGH)")
    void missingOtpOnDropTriggersViolation() {
        Long tripId = 22L;
        Instant now = Instant.parse("2026-10-11T12:00:00Z");

        Employee emp = TestFixtures.employee(1L, "EMP-101", Gender.MALE);
        Stop pickup = TestFixtures.stop(1L, tripId, 1, StopType.PICKUP, emp.getId(), now, null);
        Stop drop = TestFixtures.stop(2L, tripId, 2, StopType.DROP, emp.getId(), now.plusSeconds(600), "9999");

        // Zero OTP events recorded
        TripContext ctx = TripContextFactory.create(
                tripId, "run-1", detectedAt, false,
                List.of(pickup, drop), List.of(emp), List.of(), List.of(), List.of(), config
        );

        List<Violation> violations = rule.evaluate(ctx);
        assertThat(violations).hasSize(1);
        Violation v = violations.get(0);
        assertThat(v.getType()).isEqualTo(ViolationType.OTP_MISMATCH);
        assertThat(v.getSeverity()).isEqualTo(Severity.HIGH);
        assertThat(v.getStopId()).isEqualTo(drop.getId());
        assertThat(v.getEvidence()).contains("No OTP entered (MISSING)");
    }

    @Test
    @DisplayName("Exact duplicate OTP events (same stop and occurredAt) are ignored")
    void duplicateOtpEventsIgnored() {
        Long tripId = 23L;
        Instant now = Instant.parse("2026-10-11T12:00:00Z");
        Instant otpTime = now.plusSeconds(605);

        Employee emp = TestFixtures.employee(1L, "EMP-101", Gender.MALE);
        Stop drop = TestFixtures.stop(2L, tripId, 1, StopType.DROP, emp.getId(), now.plusSeconds(600), "9999");

        // Two identical OTP mismatch events (e.g., duplicated network retry with same timestamp)
        TripEvent e1 = TestFixtures.otpEvent(101L, tripId, drop.getId(), false, otpTime);
        TripEvent e2 = TestFixtures.otpEvent(102L, tripId, drop.getId(), false, otpTime);

        TripContext ctx = TripContextFactory.create(
                tripId, "run-1", detectedAt, false,
                List.of(drop), List.of(emp), List.of(), List.of(), List.of(e1, e2), config
        );

        List<Violation> violations = rule.evaluate(ctx);
        // Only 1 violation emitted for this stop
        assertThat(violations).hasSize(1);
    }
}

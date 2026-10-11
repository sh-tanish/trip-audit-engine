package com.example.tripaudit.rules;

import com.example.tripaudit.config.AuditProperties;
import com.example.tripaudit.domain.Employee;
import com.example.tripaudit.domain.Gender;
import com.example.tripaudit.domain.Severity;
import com.example.tripaudit.domain.Stop;
import com.example.tripaudit.domain.StopType;
import com.example.tripaudit.domain.TripEvent;
import com.example.tripaudit.domain.TripEventType;
import com.example.tripaudit.domain.Violation;
import com.example.tripaudit.domain.ViolationType;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NightSafetyRuleTest {

    private final NightSafetyRule rule = new NightSafetyRule();
    private final AuditProperties config = TripContextFactory.defaultConfig();
    private final Instant detectedAt = Instant.parse("2026-10-11T20:00:00Z");

    @Test
    @DisplayName("Woman last drop at 11:10 PM, no escort => NIGHT_SAFETY (HIGH)")
    void womanLastDropAtNightNoEscortTriggersViolation() {
        Long tripId = 10L;
        // 11:10 PM IST (23:10) is 17:40 UTC
        Instant dropTime = Instant.parse("2026-10-11T17:40:00Z"); // 11:10 PM IST (23:10)
        Instant pickupTime = Instant.parse("2026-10-11T15:30:00Z"); // 09:00 PM IST (21:00, outside night window)

        Employee woman = TestFixtures.employee(1L, "EMP-101", Gender.FEMALE);
        Stop pickup = TestFixtures.stop(101L, tripId, 1, StopType.PICKUP, woman.getId(), pickupTime, null);
        Stop drop = TestFixtures.stop(102L, tripId, 2, StopType.DROP, woman.getId(), dropTime, "1234");

        TripContext ctx = TripContextFactory.create(
                tripId, "run-1", detectedAt, false,
                List.of(pickup, drop), List.of(woman), List.of(), List.of(), List.of(), config
        );

        List<Violation> violations = rule.evaluate(ctx);

        assertThat(violations).hasSize(1);
        Violation v = violations.get(0);
        assertThat(v.getType()).isEqualTo(ViolationType.NIGHT_SAFETY);
        assertThat(v.getSeverity()).isEqualTo(Severity.HIGH);
        assertThat(v.getStopId()).isEqualTo(drop.getId());
        assertThat(v.getSubjectKey()).isEqualTo(String.valueOf(drop.getId()));
        assertThat(v.getEvidence()).contains("Last drop at 23:10");
        assertThat(v.getEvidence()).contains("passenger alone, no escort — escort required");
        // No personal data in evidence
        assertThat(v.getEvidence()).doesNotContain("EMP-101");
        assertThat(v.getEvidence()).doesNotContain("1234");
    }

    @Test
    @DisplayName("Same trip with escort assigned => no violation")
    void sameTripWithEscortAssignedGivesNoViolation() {
        Long tripId = 10L;
        Instant dropTime = Instant.parse("2026-10-11T17:40:00Z"); // 23:10 IST
        Instant pickupTime = dropTime.minusSeconds(1800);

        Employee woman = TestFixtures.employee(1L, "EMP-101", Gender.FEMALE);
        Stop pickup = TestFixtures.stop(101L, tripId, 1, StopType.PICKUP, woman.getId(), pickupTime, null);
        Stop drop = TestFixtures.stop(102L, tripId, 2, StopType.DROP, woman.getId(), dropTime, "1234");

        TripContext ctx = TripContextFactory.create(
                tripId, "run-1", detectedAt, true, // escortAssigned = true
                List.of(pickup, drop), List.of(woman), List.of(), List.of(), List.of(), config
        );

        List<Violation> violations = rule.evaluate(ctx);
        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("A man last drop => no violation")
    void manLastDropGivesNoViolation() {
        Long tripId = 11L;
        Instant dropTime = Instant.parse("2026-10-11T17:40:00Z"); // 23:10 IST
        Instant pickupTime = dropTime.minusSeconds(1800);

        Employee man = TestFixtures.employee(2L, "EMP-102", Gender.MALE);
        Stop pickup = TestFixtures.stop(201L, tripId, 1, StopType.PICKUP, man.getId(), pickupTime, null);
        Stop drop = TestFixtures.stop(202L, tripId, 2, StopType.DROP, man.getId(), dropTime, "1234");

        TripContext ctx = TripContextFactory.create(
                tripId, "run-1", detectedAt, false,
                List.of(pickup, drop), List.of(man), List.of(), List.of(), List.of(), config
        );

        List<Violation> violations = rule.evaluate(ctx);
        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("A woman who is NOT alone at her drop => no violation")
    void womanNotAloneAtDropGivesNoViolation() {
        Long tripId = 12L;
        Instant baseTime = Instant.parse("2026-10-11T17:00:00Z"); // 22:30 IST

        Employee woman = TestFixtures.employee(1L, "EMP-101", Gender.FEMALE);
        Employee man = TestFixtures.employee(2L, "EMP-102", Gender.MALE);

        // Sequence: Pickup Emp1 (man), Pickup Emp2 (woman), Drop Emp2 (woman) while Emp1 is still onboard
        Stop s1 = TestFixtures.stop(301L, tripId, 1, StopType.PICKUP, man.getId(), baseTime, null);
        Stop s2 = TestFixtures.stop(302L, tripId, 2, StopType.PICKUP, woman.getId(), baseTime.plusSeconds(300), null);
        Stop s3 = TestFixtures.stop(303L, tripId, 3, StopType.DROP, woman.getId(), baseTime.plusSeconds(600), "1111");
        Stop s4 = TestFixtures.stop(304L, tripId, 4, StopType.DROP, man.getId(), baseTime.plusSeconds(900), "2222");

        // Here woman is dropped at s3. The last drop of the trip is s4 (a man).
        // Woman was NOT alone at s3 (onboard before s3 was 2), and she was not the last drop.
        TripContext ctx = TripContextFactory.create(
                tripId, "run-1", detectedAt, false,
                List.of(s1, s2, s3, s4), List.of(man, woman), List.of(), List.of(), List.of(), config
        );

        List<Violation> violations = rule.evaluate(ctx);
        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("First pickup a woman at night => violation")
    void firstPickupWomanAtNightTriggersViolation() {
        Long tripId = 13L;
        // 10:15 PM IST (22:15) is 16:45 UTC
        Instant pickupTime = Instant.parse("2026-10-11T16:45:00Z");
        Instant dropTime = pickupTime.plusSeconds(1800);

        Employee woman = TestFixtures.employee(1L, "EMP-101", Gender.FEMALE);
        Stop pickup = TestFixtures.stop(401L, tripId, 1, StopType.PICKUP, woman.getId(), pickupTime, null);
        // Day drop outside night window (e.g., if trip was long, but here say escort arrived or drop was outside)
        Stop drop = TestFixtures.stop(402L, tripId, 2, StopType.DROP, woman.getId(), dropTime, "1234");

        // Give escort mid-trip after pickup but before drop:
        TripEvent escortEvent = TestFixtures.escortEvent(1L, tripId, TripEventType.ESCORT_ASSIGNED, pickupTime.plusSeconds(300));

        TripContext ctx = TripContextFactory.create(
                tripId, "run-1", detectedAt, false,
                List.of(pickup, drop), List.of(woman), List.of(), List.of(), List.of(escortEvent), config
        );

        List<Violation> violations = rule.evaluate(ctx);
        assertThat(violations).hasSize(1);
        Violation v = violations.get(0);
        assertThat(v.getType()).isEqualTo(ViolationType.NIGHT_SAFETY);
        assertThat(v.getSeverity()).isEqualTo(Severity.HIGH);
        assertThat(v.getStopId()).isEqualTo(pickup.getId());
        assertThat(v.getEvidence()).contains("First pickup at 22:15");
        assertThat(v.getEvidence()).contains("no escort — escort required");
    }

    @Test
    @DisplayName("Night window crossing midnight flags 01:30 AM drop")
    void nightWindowCrossingMidnightFlagsEarlyMorningDrop() {
        Long tripId = 14L;
        // 01:30 AM IST is 20:00 UTC previous day (2026-10-11T20:00:00Z)
        Instant dropTime = Instant.parse("2026-10-11T20:00:00Z");
        Instant pickupTime = dropTime.minusSeconds(1800);

        Employee woman = TestFixtures.employee(1L, "EMP-101", Gender.FEMALE);
        Stop pickup = TestFixtures.stop(501L, tripId, 1, StopType.PICKUP, woman.getId(), pickupTime, null);
        Stop drop = TestFixtures.stop(502L, tripId, 2, StopType.DROP, woman.getId(), dropTime, "1234");

        TripContext ctx = TripContextFactory.create(
                tripId, "run-1", detectedAt, false,
                List.of(pickup, drop), List.of(woman), List.of(), List.of(), List.of(), config
        );

        List<Violation> violations = rule.evaluate(ctx);
        // Both first pickup (01:00 AM) and last drop (01:30 AM) are in the night window
        assertThat(violations).hasSize(2);
        assertThat(violations).extracting(Violation::getType).containsOnly(ViolationType.NIGHT_SAFETY);
        assertThat(violations).extracting(Violation::getStopId).containsExactly(pickup.getId(), drop.getId());
    }

    @Test
    @DisplayName("Escort removed mid-trip before drop causes violation")
    void escortRemovedMidTripCausesViolation() {
        Long tripId = 15L;
        Instant dropTime = Instant.parse("2026-10-11T17:40:00Z"); // 23:10 IST
        Instant pickupTime = dropTime.minusSeconds(1800); // 22:40 IST

        Employee woman = TestFixtures.employee(1L, "EMP-101", Gender.FEMALE);
        Stop pickup = TestFixtures.stop(601L, tripId, 1, StopType.PICKUP, woman.getId(), pickupTime, null);
        Stop drop = TestFixtures.stop(602L, tripId, 2, StopType.DROP, woman.getId(), dropTime, "1234");

        // Escort was initially present, but removed before the drop
        TripEvent removeEscort = TestFixtures.escortEvent(2L, tripId, TripEventType.ESCORT_REMOVED, pickupTime.plusSeconds(300));

        TripContext ctx = TripContextFactory.create(
                tripId, "run-1", detectedAt, true, // initially true
                List.of(pickup, drop), List.of(woman), List.of(), List.of(), List.of(removeEscort), config
        );

        List<Violation> violations = rule.evaluate(ctx);
        // Pickup had escort, but drop had no escort
        assertThat(violations).hasSize(1);
        assertThat(violations.get(0).getStopId()).isEqualTo(drop.getId());
    }
}

package com.example.tripaudit.detect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.tripaudit.domain.GpsFix;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link SustainedConditionDetector}. Pure JUnit 5, no Spring context.
 */
class SustainedConditionDetectorTest {

    private static final Instant BASE_TIME = Instant.parse("2026-10-11T12:00:00Z");
    private static final Duration MIN_DURATION_120S = Duration.ofSeconds(120);
    private static final Duration MAX_GAP_60S = Duration.ofSeconds(60);

    @Test
    void emptyAndSingleFixInputProduceNoRuns() {
        // Empty list
        List<Run<GpsFix>> emptyRuns = SustainedConditionDetector.findGpsRuns(
                List.of(),
                fix -> true,
                MIN_DURATION_120S,
                MAX_GAP_60S
        );
        assertTrue(emptyRuns.isEmpty());

        // Single fix input (duration is 0 seconds, which is < minDuration 120s)
        GpsFix singleFix = new GpsFix(1L, BASE_TIME, 12.0, 77.0, 80.0);
        List<Run<GpsFix>> singleRuns = SustainedConditionDetector.findGpsRuns(
                List.of(singleFix),
                fix -> true,
                MIN_DURATION_120S,
                MAX_GAP_60S
        );
        assertTrue(singleRuns.isEmpty());
    }

    @Test
    void singleOutOfRangePingGivesNoRun() {
        // Pings every 30s. Fix at index 1 is out-of-range, others are in-range.
        List<GpsFix> fixes = List.of(
                new GpsFix(1L, BASE_TIME, 12.0, 77.0, 40.0),                     // t=0s,  normal
                new GpsFix(1L, BASE_TIME.plusSeconds(30), 12.0, 77.0, 100.0),   // t=30s, spike (speed > 50)
                new GpsFix(1L, BASE_TIME.plusSeconds(60), 12.0, 77.0, 40.0)     // t=60s, normal
        );

        List<Run<GpsFix>> runs = SustainedConditionDetector.findGpsRuns(
                fixes,
                fix -> fix.getSpeedKmph() > 50.0,
                Duration.ofSeconds(30),
                MAX_GAP_60S
        );

        // A single spike has duration 0s, which is strictly less than minDuration (30s)
        assertTrue(runs.isEmpty(), "Single out-of-range spike must not trigger a run");
    }

    @Test
    void fourMinutesOutOfRangeGivesExactlyOneRunWithCorrectStartAndEnd() {
        // 9 fixes every 30s from t=0s to t=240s (4 minutes)
        List<GpsFix> fixes = new ArrayList<>();
        for (int i = 0; i <= 8; i++) {
            fixes.add(new GpsFix(1L, BASE_TIME.plusSeconds(i * 30L), 12.0, 77.0, 80.0));
        }

        List<Run<GpsFix>> runs = SustainedConditionDetector.findGpsRuns(
                fixes,
                fix -> fix.getSpeedKmph() > 50.0,
                MIN_DURATION_120S,
                MAX_GAP_60S
        );

        assertEquals(1, runs.size(), "Should produce exactly 1 sustained run");
        Run<GpsFix> run = runs.get(0);

        assertEquals(0, run.startIndex());
        assertEquals(8, run.endIndex());
        assertEquals(BASE_TIME, run.startTime());
        assertEquals(BASE_TIME.plusSeconds(240), run.endTime());
        assertEquals(Duration.ofSeconds(240), run.duration());
        assertEquals(9, run.samples().size());
    }

    @Test
    void gapLargerThanMaxGapSplitsRuns() {
        // Part 1: t=0s to t=120s (5 fixes every 30s -> duration 120s >= 120s)
        // Gap: next fix is at t=210s (gap is 90s > maxGap 60s)
        // Part 2: t=210s to t=330s (5 fixes every 30s -> duration 120s >= 120s)
        List<GpsFix> fixes = new ArrayList<>();
        // Part 1
        for (int i = 0; i <= 4; i++) {
            fixes.add(new GpsFix(1L, BASE_TIME.plusSeconds(i * 30L), 12.0, 77.0, 80.0));
        }
        // Part 2 (starts after a 90s gap)
        for (int i = 0; i <= 4; i++) {
            fixes.add(new GpsFix(1L, BASE_TIME.plusSeconds(210L + i * 30L), 12.0, 77.0, 80.0));
        }

        List<Run<GpsFix>> runs = SustainedConditionDetector.findGpsRuns(
                fixes,
                fix -> fix.getSpeedKmph() > 50.0,
                MIN_DURATION_120S,
                MAX_GAP_60S
        );

        assertEquals(2, runs.size(), "A gap of 90s (> 60s) must split into 2 runs");

        Run<GpsFix> run1 = runs.get(0);
        assertEquals(0, run1.startIndex());
        assertEquals(4, run1.endIndex());
        assertEquals(BASE_TIME, run1.startTime());
        assertEquals(BASE_TIME.plusSeconds(120), run1.endTime());
        assertEquals(Duration.ofSeconds(120), run1.duration());

        Run<GpsFix> run2 = runs.get(1);
        assertEquals(5, run2.startIndex());
        assertEquals(9, run2.endIndex());
        assertEquals(BASE_TIME.plusSeconds(210), run2.startTime());
        assertEquals(BASE_TIME.plusSeconds(330), run2.endTime());
        assertEquals(Duration.ofSeconds(120), run2.duration());
    }

    @Test
    void gapEqualToMaxGapDoesNotSplitRun() {
        // Fixes at t=0s, t=60s, t=120s (gap is exactly 60s, which is <= maxGap 60s)
        List<GpsFix> fixes = List.of(
                new GpsFix(1L, BASE_TIME, 12.0, 77.0, 80.0),
                new GpsFix(1L, BASE_TIME.plusSeconds(60), 12.0, 77.0, 80.0),
                new GpsFix(1L, BASE_TIME.plusSeconds(120), 12.0, 77.0, 80.0)
        );

        List<Run<GpsFix>> runs = SustainedConditionDetector.findGpsRuns(
                fixes,
                fix -> fix.getSpeedKmph() > 50.0,
                MIN_DURATION_120S,
                MAX_GAP_60S
        );

        assertEquals(1, runs.size());
        assertEquals(Duration.ofSeconds(120), runs.get(0).duration());
    }

    @Test
    void conditionCeasingAndResumingSplitsRuns() {
        // t=0..60s speeding (3 fixes: 0, 30, 60 -> duration 60s)
        // t=90s normal speed
        // t=120..180s speeding (3 fixes: 120, 150, 180 -> duration 60s)
        List<GpsFix> fixes = List.of(
                new GpsFix(1L, BASE_TIME, 12.0, 77.0, 70.0),
                new GpsFix(1L, BASE_TIME.plusSeconds(30), 12.0, 77.0, 70.0),
                new GpsFix(1L, BASE_TIME.plusSeconds(60), 12.0, 77.0, 70.0),
                new GpsFix(1L, BASE_TIME.plusSeconds(90), 12.0, 77.0, 30.0), // normal
                new GpsFix(1L, BASE_TIME.plusSeconds(120), 12.0, 77.0, 70.0),
                new GpsFix(1L, BASE_TIME.plusSeconds(150), 12.0, 77.0, 70.0),
                new GpsFix(1L, BASE_TIME.plusSeconds(180), 12.0, 77.0, 70.0)
        );

        List<Run<GpsFix>> runs = SustainedConditionDetector.findGpsRuns(
                fixes,
                fix -> fix.getSpeedKmph() > 50.0,
                Duration.ofSeconds(60),
                MAX_GAP_60S
        );

        assertEquals(2, runs.size());
        assertEquals(0, runs.get(0).startIndex());
        assertEquals(2, runs.get(0).endIndex());
        assertEquals(4, runs.get(1).startIndex());
        assertEquals(6, runs.get(1).endIndex());
    }
}

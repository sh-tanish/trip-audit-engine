package com.example.tripaudit.detect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.tripaudit.domain.GpsFix;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link GpsTrail}. Pure JUnit 5, no Spring context.
 */
class GpsTrailTest {

    private static final Instant T0 = Instant.parse("2026-10-11T12:00:00Z");
    private static final Instant T30 = Instant.parse("2026-10-11T12:00:30Z");
    private static final Instant T60 = Instant.parse("2026-10-11T12:01:00Z");
    private static final Instant T90 = Instant.parse("2026-10-11T12:01:30Z");

    @Test
    void emptyAndSingleFixInputHandling() {
        assertTrue(GpsTrail.normalize(null).isEmpty());
        assertTrue(GpsTrail.normalize(List.of()).isEmpty());

        GpsFix single = new GpsFix(1L, T0, 12.0, 77.0, 45.0);
        List<GpsFix> normalizedSingle = GpsTrail.normalize(List.of(single));
        assertEquals(1, normalizedSingle.size());
        assertEquals(T0, normalizedSingle.get(0).getRecordedAt());
    }

    @Test
    void sortsUnsortedFixesChronologically() {
        GpsFix fix0 = new GpsFix(1L, T0, 12.0, 77.0, 40.0);
        GpsFix fix30 = new GpsFix(1L, T30, 12.0, 77.0, 42.0);
        GpsFix fix60 = new GpsFix(1L, T60, 12.0, 77.0, 44.0);
        GpsFix fix90 = new GpsFix(1L, T90, 12.0, 77.0, 46.0);

        // Input in shuffled order: T60, T0, T90, T30
        List<GpsFix> unsorted = List.of(fix60, fix0, fix90, fix30);
        List<GpsFix> cleaned = GpsTrail.normalize(unsorted);

        assertEquals(4, cleaned.size());
        assertEquals(T0, cleaned.get(0).getRecordedAt());
        assertEquals(T30, cleaned.get(1).getRecordedAt());
        assertEquals(T60, cleaned.get(2).getRecordedAt());
        assertEquals(T90, cleaned.get(3).getRecordedAt());
    }

    @Test
    void dropsDuplicateTimestampsKeepingFirstEncountered() {
        GpsFix firstAtT0 = new GpsFix(1L, T0, 12.0, 77.0, 40.0);
        GpsFix secondAtT0 = new GpsFix(1L, T0, 12.0, 77.0, 99.0); // duplicate timestamp
        GpsFix fix30 = new GpsFix(1L, T30, 12.0, 77.0, 50.0);
        GpsFix duplicate30 = new GpsFix(1L, T30, 12.0, 77.0, 88.0); // duplicate timestamp

        List<GpsFix> raw = List.of(firstAtT0, secondAtT0, fix30, duplicate30);
        List<GpsFix> cleaned = GpsTrail.normalize(raw);

        assertEquals(2, cleaned.size());
        assertEquals(T0, cleaned.get(0).getRecordedAt());
        assertEquals(40.0, cleaned.get(0).getSpeedKmph(), "Must keep the first fix encountered for that timestamp");
        assertEquals(T30, cleaned.get(1).getRecordedAt());
        assertEquals(50.0, cleaned.get(1).getSpeedKmph());
    }

    @Test
    void unsortedAndDuplicateFixesFedIntoSustainedConditionDetector() {
        // Build 5 points at 0s, 30s, 60s, 90s, 120s with some duplicates and shuffled
        GpsFix f0 = new GpsFix(1L, T0, 12.0, 77.0, 80.0);
        GpsFix f0Dup = new GpsFix(1L, T0, 12.0, 77.0, 80.0);
        GpsFix f30 = new GpsFix(1L, T30, 12.0, 77.0, 80.0);
        GpsFix f60 = new GpsFix(1L, T60, 12.0, 77.0, 80.0);
        GpsFix f60Dup = new GpsFix(1L, T60, 12.0, 77.0, 80.0);
        GpsFix f90 = new GpsFix(1L, T90, 12.0, 77.0, 80.0);
        GpsFix f120 = new GpsFix(1L, T0.plusSeconds(120), 12.0, 77.0, 80.0);

        List<GpsFix> messy = List.of(f60, f0, f120, f0Dup, f90, f30, f60Dup);

        // Normalize first
        List<GpsFix> cleaned = GpsTrail.normalize(messy);
        assertEquals(5, cleaned.size());

        // Feed to sustained condition detector
        List<Run<GpsFix>> runs = SustainedConditionDetector.findGpsRuns(
                cleaned,
                fix -> fix.getSpeedKmph() > 50.0,
                Duration.ofSeconds(120),
                Duration.ofSeconds(60)
        );

        assertEquals(1, runs.size());
        assertEquals(Duration.ofSeconds(120), runs.get(0).duration());
        assertEquals(T0, runs.get(0).startTime());
        assertEquals(T0.plusSeconds(120), runs.get(0).endTime());
    }
}

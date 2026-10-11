package com.example.tripaudit.detect;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Immutable record representing a maximal consecutive run of samples
 * where a sustained condition held.
 *
 * @param <T> sample type
 * @param startIndex 0-based index of the first sample of the run in the input list
 * @param endIndex 0-based index of the last sample of the run in the input list (inclusive)
 * @param startTime timestamp of the first sample
 * @param endTime timestamp of the last sample
 * @param duration duration between startTime and endTime (endTime - startTime)
 * @param samples the immutable list of samples comprising this run
 */
public record Run<T>(
        int startIndex,
        int endIndex,
        Instant startTime,
        Instant endTime,
        Duration duration,
        List<T> samples
) {
    public Run {
        // Defensive copy ensuring immutability of the sample collection
        samples = samples == null ? List.of() : List.copyOf(samples);
    }
}

package com.example.tripaudit.detect;

import com.example.tripaudit.domain.GpsFix;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;

public final class SustainedConditionDetector {

    private SustainedConditionDetector() {}

    /**
     * Finds runs where {@code predicate} holds over {@code samples}.
     *
     * Time complexity: O(N) where N is the number of samples. The input is scanned once,
     * and each sample is examined in O(1) time. Sublists are sliced into run objects without
     * duplicate traversals.
     * Space complexity: O(1) auxiliary space beyond the output list of runs (or O(N) worst-case
     * output storage if runs span all input samples).
     *
     * @param <T> sample type
     * @param samples time-ordered sequence of samples
     * @param timeExtractor function extracting the timestamp from a sample
     * @param predicate condition to test on each sample
     * @param minDuration minimum required duration (inclusive) for a run to qualify
     * @param maxGap maximum allowed time between consecutive samples before splitting
     * @return list of qualifying runs
     */
    public static <T> List<Run<T>> findRuns(
            List<T> samples,
            Function<T, Instant> timeExtractor,
            Predicate<T> predicate,
            Duration minDuration,
            Duration maxGap
    ) {
        Objects.requireNonNull(timeExtractor, "timeExtractor must not be null");
        Objects.requireNonNull(predicate, "predicate must not be null");
        Objects.requireNonNull(minDuration, "minDuration must not be null");
        Objects.requireNonNull(maxGap, "maxGap must not be null");

        if (samples == null || samples.isEmpty()) {
            return List.of();
        }

        List<Run<T>> runs = new ArrayList<>();
        int runStart = -1;

        for (int i = 0; i < samples.size(); i++) {
            T current = samples.get(i);
            Instant currentTime = timeExtractor.apply(current);

            boolean gapTooBig = false;
            if (i > 0) {
                Instant prevTime = timeExtractor.apply(samples.get(i - 1));
                Duration gap = Duration.between(prevTime, currentTime);
                if (gap.compareTo(maxGap) > 0) {
                    gapTooBig = true;
                }
            }

            boolean matches = predicate.test(current);

            // If a run is in progress and either the condition ceased or the gap was exceeded, close the run
            if (runStart != -1 && (!matches || gapTooBig)) {
                closeRunIfQualified(samples, runStart, i - 1, timeExtractor, minDuration, runs);
                runStart = -1;
            }

            // Start a new run if the predicate matches and no run is currently active
            if (matches && runStart == -1) {
                runStart = i;
            }
        }

        // Close any trailing run spanning up to the end of the input
        if (runStart != -1) {
            closeRunIfQualified(samples, runStart, samples.size() - 1, timeExtractor, minDuration, runs);
        }

        return List.copyOf(runs);
    }

    /**
     * Time complexity: O(N) where N is the number of fixes.
     * Space complexity: O(1) auxiliary space beyond the output list.
     */
    public static List<Run<GpsFix>> findGpsRuns(
            List<GpsFix> fixes,
            Predicate<GpsFix> predicate,
            Duration minDuration,
            Duration maxGap
    ) {
        return findRuns(fixes, GpsFix::getRecordedAt, predicate, minDuration, maxGap);
    }

    private static <T> void closeRunIfQualified(
            List<T> samples,
            int startIdx,
            int endIdx,
            Function<T, Instant> timeExtractor,
            Duration minDuration,
            List<Run<T>> runs
    ) {
        Instant startTime = timeExtractor.apply(samples.get(startIdx));
        Instant endTime = timeExtractor.apply(samples.get(endIdx));
        Duration duration = Duration.between(startTime, endTime);

        if (duration.compareTo(minDuration) >= 0) {
            List<T> runSamples = samples.subList(startIdx, endIdx + 1);
            runs.add(new Run<>(startIdx, endIdx, startTime, endTime, duration, runSamples));
        }
    }
}

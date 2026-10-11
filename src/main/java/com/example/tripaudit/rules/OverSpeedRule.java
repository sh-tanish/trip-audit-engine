package com.example.tripaudit.rules;

import com.example.tripaudit.detect.Run;
import com.example.tripaudit.detect.SustainedConditionDetector;
import com.example.tripaudit.domain.Severity;
import com.example.tripaudit.domain.Violation;
import com.example.tripaudit.domain.ViolationType;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * Evaluates speed limit adherence:
 * Raises an OVER_SPEED violation if the vehicle speeds beyond the nearest route segment limit
 * for at least minDuration (default 30 s) with pings no more than maxGap apart.
 *
 * Severity is CRITICAL if peak speed > limit * criticalFactor (default 1.5), otherwise MEDIUM.
 * Isolated speed spikes (e.g., duration 0) are naturally ignored.
 */
@Component
public class OverSpeedRule implements SafetyRule {

    @Override
    public List<Violation> evaluate(TripContext ctx) {
        if (ctx.matchedFixes().isEmpty() || ctx.config() == null || ctx.config().speed() == null) {
            return List.of();
        }

        Duration minDuration = ctx.config().speed().minDuration();
        double criticalFactor = ctx.config().speed().criticalFactor();
        Duration maxGap = ctx.config().gps() != null ? ctx.config().gps().maxGap() : Duration.ofSeconds(60);

        List<Run<MatchedFix>> runs = SustainedConditionDetector.findRuns(
                ctx.matchedFixes(),
                MatchedFix::recordedAt,
                fix -> fix.speedKmph() > ctx.speedLimitForSegment(fix.segmentIndex()),
                minDuration,
                maxGap
        );

        List<Violation> violations = new ArrayList<>(runs.size());

        for (Run<MatchedFix> run : runs) {
            MatchedFix peakFix = null;
            double maxRatio = 0.0;

            for (MatchedFix fix : run.samples()) {
                double limit = ctx.speedLimitForSegment(fix.segmentIndex());
                double ratio = limit > 0 ? fix.speedKmph() / limit : 1.0;
                if (ratio > maxRatio) {
                    maxRatio = ratio;
                    peakFix = fix;
                }
            }

            Severity severity = (maxRatio > criticalFactor) ? Severity.CRITICAL : Severity.MEDIUM;
            String subjectKey = String.valueOf(run.startTime().toEpochMilli());

            double peakSpeed = peakFix != null ? peakFix.speedKmph() : 0.0;
            double peakLimit = peakFix != null ? ctx.speedLimitForSegment(peakFix.segmentIndex()) : 0.0;
            int peakSeg = peakFix != null ? peakFix.segmentIndex() : -1;

            String evidence = String.format(
                    Locale.ROOT,
                    "Over-speed from %s to %s (duration %ds), peak %.1f km/h in %.1f km/h zone (ratio %.2f) near segment %d",
                    run.startTime(), run.endTime(), run.duration().toSeconds(), peakSpeed, peakLimit, maxRatio, peakSeg
            );

            violations.add(new Violation(
                    ctx.tripId(),
                    ViolationType.OVER_SPEED,
                    severity,
                    getClass().getSimpleName(),
                    subjectKey,
                    null,
                    null,
                    evidence,
                    ctx.detectedAt(),
                    ctx.auditRunId()
            ));
        }

        return List.copyOf(violations);
    }
}

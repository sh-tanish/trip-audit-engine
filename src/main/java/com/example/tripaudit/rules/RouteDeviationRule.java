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
 * Evaluates route adherence:
 * Raises a HIGH severity violation if the vehicle is sustained off-route (distance > thresholdMeters)
 * for at least minDuration with pings no more than maxGap apart.
 * Single GPS blips are naturally ignored.
 */
@Component
public class RouteDeviationRule implements SafetyRule {

    @Override
    public List<Violation> evaluate(TripContext ctx) {
        if (ctx.matchedFixes().isEmpty() || ctx.config() == null || ctx.config().route() == null) {
            return List.of();
        }

        double thresholdMeters = ctx.config().route().thresholdMeters();
        Duration minDuration = ctx.config().route().minDuration();
        Duration maxGap = ctx.config().gps() != null ? ctx.config().gps().maxGap() : Duration.ofSeconds(60);

        List<Run<MatchedFix>> runs = SustainedConditionDetector.findRuns(
                ctx.matchedFixes(),
                MatchedFix::recordedAt,
                fix -> fix.distanceMeters() > thresholdMeters,
                minDuration,
                maxGap
        );

        List<Violation> violations = new ArrayList<>(runs.size());

        for (Run<MatchedFix> run : runs) {
            MatchedFix firstFix = run.samples().get(0);
            int deviatingSegment = firstFix.segmentIndex();
            double maxDeviation = run.samples().stream()
                    .mapToDouble(MatchedFix::distanceMeters)
                    .max()
                    .orElse(thresholdMeters);

            String subjectKey = String.valueOf(run.startTime().toEpochMilli());
            String evidence = String.format(
                    Locale.ROOT,
                    "Route deviation: max %.1fm off near segment %d from %s to %s (duration %ds)",
                    maxDeviation, deviatingSegment, run.startTime(), run.endTime(), run.duration().toSeconds()
            );

            violations.add(new Violation(
                    ctx.tripId(),
                    ViolationType.ROUTE_DEVIATION,
                    Severity.HIGH,
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

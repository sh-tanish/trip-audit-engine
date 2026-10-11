package com.example.tripaudit.rules;

import com.example.tripaudit.domain.Severity;
import com.example.tripaudit.domain.Stop;
import com.example.tripaudit.domain.StopType;
import com.example.tripaudit.domain.TripEvent;
import com.example.tripaudit.domain.TripEventType;
import com.example.tripaudit.domain.Violation;
import com.example.tripaudit.domain.ViolationType;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Evaluates OTP compliance at drop stops:
 * - Every DROP stop must have an OTP_ENTERED event with a successful match.
 * - If any OTP entry attempt for a drop stop fails (otpMatched == false), an OTP_MISMATCH violation is raised.
 * - If zero OTP_ENTERED events exist for a drop stop, an OTP_MISMATCH (reason MISSING) violation is raised.
 * - Exact duplicate OTP events (same stopId and occurredAt) are ignored.
 */
@Component
public class OtpAtDropRule implements SafetyRule {

    @Override
    public List<Violation> evaluate(TripContext ctx) {
        if (ctx.stops().isEmpty()) {
            return List.of();
        }

        List<Violation> violations = new ArrayList<>();

        for (Stop stop : ctx.stops()) {
            if (stop.getType() != StopType.DROP) {
                continue;
            }

            // Find all OTP_ENTERED events for this stop, ignoring exact duplicates (same occurredAt)
            Set<Instant> seenTimestamps = new HashSet<>();
            List<TripEvent> stopEvents = new ArrayList<>();

            for (TripEvent e : ctx.events()) {
                if (e.getType() == TripEventType.OTP_ENTERED && Objects.equals(e.getStopId(), stop.getId())) {
                    if (seenTimestamps.add(e.getOccurredAt())) {
                        stopEvents.add(e);
                    }
                }
            }

            if (stopEvents.isEmpty()) {
                // No OTP was entered for this drop
                String evidence = "No OTP entered (MISSING) for drop stop " + stop.getId();
                violations.add(new Violation(
                        ctx.tripId(),
                        ViolationType.OTP_MISMATCH,
                        Severity.HIGH,
                        getClass().getSimpleName(),
                        String.valueOf(stop.getId()),
                        stop.getId(),
                        stop.getEmployeeId(),
                        evidence,
                        ctx.detectedAt(),
                        ctx.auditRunId()
                ));
            } else {
                boolean hasMismatch = stopEvents.stream()
                        .anyMatch(e -> Boolean.FALSE.equals(e.getOtpMatched()));

                if (hasMismatch) {
                    String evidence = "OTP verification failed (MISMATCH) for drop stop " + stop.getId();
                    violations.add(new Violation(
                            ctx.tripId(),
                            ViolationType.OTP_MISMATCH,
                            Severity.HIGH,
                            getClass().getSimpleName(),
                            String.valueOf(stop.getId()),
                            stop.getId(),
                            stop.getEmployeeId(),
                            evidence,
                            ctx.detectedAt(),
                            ctx.auditRunId()
                    ));
                }
            }
        }

        return List.copyOf(violations);
    }
}

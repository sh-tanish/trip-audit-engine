package com.example.tripaudit.rules;

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
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Evaluates night safety compliance:
 * A woman must not be the first pickup or the last drop alone during the night window
 * without a security escort in effect.
 */
@Component
public class NightSafetyRule implements SafetyRule {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    @Override
    public List<Violation> evaluate(TripContext ctx) {
        if (ctx.stops().isEmpty() || ctx.config() == null || ctx.config().night() == null) {
            return List.of();
        }

        NightWindow window = new NightWindow(ctx.config().night().start(), ctx.config().night().end());
        ZoneId zone = ctx.config().zone();

        List<Violation> violations = new ArrayList<>();

        // Stops in ctx are already ordered by sequence
        List<Stop> stops = ctx.stops();

        // 1. Evaluate first pickup
        Stop firstPickup = null;
        for (Stop s : stops) {
            if (s.getType() == StopType.PICKUP) {
                firstPickup = s;
                break;
            }
        }

        if (firstPickup != null) {
            Employee emp = ctx.getEmployee(firstPickup.getEmployeeId());
            boolean isFemale = emp != null && emp.getGender() == Gender.FEMALE;
            boolean inNight = window.contains(firstPickup.getPlannedTime(), zone);
            boolean escortPresent = hasEscortInEffect(firstPickup.getPlannedTime(), ctx);

            if (isFemale && inNight && !escortPresent) {
                String timeStr = firstPickup.getPlannedTime().atZone(zone).toLocalTime().format(TIME_FORMAT);
                String evidence = String.format(
                        "First pickup at %s (night window %s-%s), no escort — escort required",
                        timeStr, window.start().format(TIME_FORMAT), window.end().format(TIME_FORMAT)
                );
                violations.add(new Violation(
                        ctx.tripId(),
                        ViolationType.NIGHT_SAFETY,
                        Severity.HIGH,
                        getClass().getSimpleName(),
                        String.valueOf(firstPickup.getId()),
                        firstPickup.getId(),
                        firstPickup.getEmployeeId(),
                        evidence,
                        ctx.detectedAt(),
                        ctx.auditRunId()
                ));
            }
        }

        // 2. Evaluate last drop alone
        // Track onboard passenger count to determine whether passenger was alone
        Stop lastDrop = null;
        int onboardBeforeLastDrop = 0;
        int currentOnboard = 0;

        for (Stop s : stops) {
            if (s.getType() == StopType.PICKUP) {
                currentOnboard++;
            } else if (s.getType() == StopType.DROP) {
                lastDrop = s;
                onboardBeforeLastDrop = currentOnboard;
                currentOnboard--;
            }
        }

        if (lastDrop != null) {
            Employee emp = ctx.getEmployee(lastDrop.getEmployeeId());
            boolean isFemale = emp != null && emp.getGender() == Gender.FEMALE;
            boolean alone = (onboardBeforeLastDrop == 1);
            boolean inNight = window.contains(lastDrop.getPlannedTime(), zone);
            boolean escortPresent = hasEscortInEffect(lastDrop.getPlannedTime(), ctx);

            if (isFemale && alone && inNight && !escortPresent) {
                String timeStr = lastDrop.getPlannedTime().atZone(zone).toLocalTime().format(TIME_FORMAT);
                String evidence = String.format(
                        "Last drop at %s (night window %s-%s), passenger alone, no escort — escort required",
                        timeStr, window.start().format(TIME_FORMAT), window.end().format(TIME_FORMAT)
                );
                violations.add(new Violation(
                        ctx.tripId(),
                        ViolationType.NIGHT_SAFETY,
                        Severity.HIGH,
                        getClass().getSimpleName(),
                        String.valueOf(lastDrop.getId()),
                        lastDrop.getId(),
                        lastDrop.getEmployeeId(),
                        evidence,
                        ctx.detectedAt(),
                        ctx.auditRunId()
                ));
            }
        }

        return List.copyOf(violations);
    }

    /**
     * Determines whether an escort was in effect at the stop's planned time.
     * Takes the initial trip flag, then applies chronological escort assignment/removal events up to planned time.
     */
    private boolean hasEscortInEffect(Instant plannedTime, TripContext ctx) {
        boolean escort = ctx.initialEscortAssigned();
        if (ctx.events().isEmpty()) {
            return escort;
        }

        List<TripEvent> relevant = ctx.events().stream()
                .filter(e -> e.getType() == TripEventType.ESCORT_ASSIGNED || e.getType() == TripEventType.ESCORT_REMOVED)
                .filter(e -> !e.getOccurredAt().isAfter(plannedTime))
                .sorted(Comparator.comparing(TripEvent::getOccurredAt))
                .toList();

        for (TripEvent e : relevant) {
            escort = (e.getType() == TripEventType.ESCORT_ASSIGNED);
        }

        return escort;
    }
}

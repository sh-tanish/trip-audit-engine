package com.example.tripaudit.rules;

import com.example.tripaudit.config.AuditProperties;
import com.example.tripaudit.domain.Employee;
import com.example.tripaudit.domain.RouteSegment;
import com.example.tripaudit.domain.Stop;
import com.example.tripaudit.domain.TripEvent;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Immutable snapshot of all facts required to evaluate safety rules for one trip audit run.
 *
 * Rules receive this context and evaluate safety conditions as pure functions.
 */
public record TripContext(
        Long tripId,
        String auditRunId,
        Instant detectedAt,
        boolean initialEscortAssigned,
        List<Stop> stops,
        Map<Long, Employee> employees,
        List<RouteSegment> routeSegments,
        List<MatchedFix> matchedFixes,
        List<TripEvent> events,
        AuditProperties config
) {
    public TripContext {
        stops = stops == null ? List.of() : List.copyOf(stops);
        employees = employees == null ? Map.of() : Map.copyOf(employees);
        routeSegments = routeSegments == null ? List.of() : List.copyOf(routeSegments);
        matchedFixes = matchedFixes == null ? List.of() : List.copyOf(matchedFixes);
        events = events == null ? List.of() : List.copyOf(events);
    }

    /**
     * Looks up an employee by their ID.
     */
    public Employee getEmployee(Long employeeId) {
        return employees.get(employeeId);
    }

    /**
     * Returns the speed limit in km/h for the route segment at the given index.
     */
    public double speedLimitForSegment(int segmentIndex) {
        if (segmentIndex >= 0 && segmentIndex < routeSegments.size()) {
            return routeSegments.get(segmentIndex).getSpeedLimitKmph();
        }
        return Double.POSITIVE_INFINITY;
    }
}

package com.example.tripaudit.rules;

import com.example.tripaudit.config.AuditProperties;
import com.example.tripaudit.detect.GpsTrail;
import com.example.tripaudit.detect.RouteMatcher;
import com.example.tripaudit.detect.SegmentMatch;
import com.example.tripaudit.domain.Employee;
import com.example.tripaudit.domain.GeoPoint;
import com.example.tripaudit.domain.GpsFix;
import com.example.tripaudit.domain.RouteSegment;
import com.example.tripaudit.domain.Stop;
import com.example.tripaudit.domain.TripEvent;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Factory for assembling a complete, validated {@link TripContext} from raw trip data.
 *
 * Sorts stops by sequence, normalizes and dedups raw GPS fixes using {@link GpsTrail},
 * and maps each cleaned fix to its nearest planned route segment using {@link RouteMatcher}.
 */
public final class TripContextFactory {

    private TripContextFactory() {}

    /**
     * Builds a default AuditProperties configuration based on the case study / DESIGN.md defaults.
     */
    public static AuditProperties defaultConfig() {
        return new AuditProperties(
                ZoneId.of("Asia/Kolkata"),
                new AuditProperties.Night(LocalTime.of(22, 0), LocalTime.of(6, 0)),
                new AuditProperties.Route(500.0, Duration.ofSeconds(120)),
                new AuditProperties.Speed(Duration.ofSeconds(30), 1.5),
                new AuditProperties.Gps(Duration.ofSeconds(60))
        );
    }

    /**
     * Creates an immutable TripContext from raw domain collections.
     */
    public static TripContext create(
            Long tripId,
            String auditRunId,
            Instant detectedAt,
            boolean initialEscortAssigned,
            List<Stop> rawStops,
            List<Employee> rawEmployees,
            List<RouteSegment> rawSegments,
            List<GpsFix> rawFixes,
            List<TripEvent> rawEvents,
            AuditProperties config
    ) {
        // 1. Sort stops by sequence
        List<Stop> sortedStops = List.of();
        if (rawStops != null && !rawStops.isEmpty()) {
            List<Stop> stopList = new ArrayList<>(rawStops);
            stopList.sort(Comparator.comparingInt(Stop::getSequence));
            sortedStops = List.copyOf(stopList);
        }

        // 2. Index employees by ID
        Map<Long, Employee> employeeMap = Map.of();
        if (rawEmployees != null && !rawEmployees.isEmpty()) {
            employeeMap = rawEmployees.stream()
                    .collect(Collectors.toMap(Employee::getId, Function.identity(), (e1, e2) -> e1));
        }

        // 3. Sort route segments by seq
        List<RouteSegment> sortedSegments = List.of();
        if (rawSegments != null && !rawSegments.isEmpty()) {
            List<RouteSegment> segList = new ArrayList<>(rawSegments);
            segList.sort(Comparator.comparingInt(RouteSegment::getSeq));
            sortedSegments = List.copyOf(segList);
        }

        // 4. Normalize GPS fixes and match to route
        List<MatchedFix> matchedFixes = List.of();
        if (rawFixes != null && !rawFixes.isEmpty() && !sortedSegments.isEmpty()) {
            List<GpsFix> cleanedFixes = GpsTrail.normalize(rawFixes);
            List<MatchedFix> matches = new ArrayList<>(cleanedFixes.size());
            for (GpsFix fix : cleanedFixes) {
                GeoPoint pt = new GeoPoint(fix.getLat(), fix.getLon());
                SegmentMatch match = RouteMatcher.nearest(pt, sortedSegments);
                matches.add(new MatchedFix(fix, match));
            }
            matchedFixes = List.copyOf(matches);
        }

        // 5. Events copy
        List<TripEvent> eventList = rawEvents == null ? List.of() : List.copyOf(rawEvents);

        return new TripContext(
                tripId,
                auditRunId,
                detectedAt,
                initialEscortAssigned,
                sortedStops,
                employeeMap,
                sortedSegments,
                matchedFixes,
                eventList,
                config
        );
    }
}

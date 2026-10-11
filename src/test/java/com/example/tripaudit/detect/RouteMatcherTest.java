package com.example.tripaudit.detect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.tripaudit.domain.GeoPoint;
import com.example.tripaudit.domain.RouteSegment;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link RouteMatcher}. Pure JUnit 5, no Spring context.
 */
class RouteMatcherTest {

    @Test
    void matchesToNearestSegmentAmongMultipleSegments() {
        RouteSegment seg0 = new RouteSegment(1L, 0, new GeoPoint(12.0, 77.0), new GeoPoint(12.0, 77.1), 50.0);
        RouteSegment seg1 = new RouteSegment(1L, 1, new GeoPoint(12.0, 77.1), new GeoPoint(12.0, 77.2), 40.0);
        RouteSegment seg2 = new RouteSegment(1L, 2, new GeoPoint(12.0, 77.2), new GeoPoint(12.0, 77.3), 30.0);

        List<RouteSegment> route = List.of(seg0, seg1, seg2);

        // Point near segment 1
        GeoPoint p = new GeoPoint(12.001, 77.15);
        SegmentMatch match = RouteMatcher.nearest(p, route);

        assertEquals(1, match.segmentIndex());
        // 0.001 deg lat is ~111m
        assertTrue(match.distanceMeters() > 100.0 && match.distanceMeters() < 120.0);
    }

    @Test
    void throwsOnNullOrEmptySegments() {
        assertThrows(IllegalArgumentException.class, () -> RouteMatcher.nearest(new GeoPoint(12.0, 77.0), null));
        assertThrows(IllegalArgumentException.class, () -> RouteMatcher.nearest(new GeoPoint(12.0, 77.0), List.of()));
    }
}

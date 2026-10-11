package com.example.tripaudit.detect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.tripaudit.domain.GeoPoint;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link GeoMath}. Pure JUnit 5, no Spring context.
 */
class GeoMathTest {

    @Test
    void pointOnRouteGivesApproximatelyZeroMeters() {
        GeoPoint a = new GeoPoint(12.9716, 77.5946);
        GeoPoint b = new GeoPoint(12.9352, 77.6245);

        // Point exactly at start vertex
        double distStart = GeoMath.pointToSegmentMeters(a, a, b);
        assertEquals(0.0, distStart, 1e-4);

        // Point exactly at end vertex
        double distEnd = GeoMath.pointToSegmentMeters(b, a, b);
        assertEquals(0.0, distEnd, 1e-4);

        // Midpoint along the straight segment
        GeoPoint midpoint = new GeoPoint((a.lat() + b.lat()) / 2.0, (a.lon() + b.lon()) / 2.0);
        double distMid = GeoMath.pointToSegmentMeters(midpoint, a, b);
        assertEquals(0.0, distMid, 0.05);

        // Polyline test
        double polyDist = GeoMath.pointToPolylineMeters(midpoint, List.of(a, b));
        assertEquals(0.0, polyDist, 0.05);
    }

    @Test
    void pointOffsetByPoint0072DegreesLatitudeGivesApproximately800Meters() {
        // Base route segment oriented horizontally
        GeoPoint a = new GeoPoint(12.0, 77.0);
        GeoPoint b = new GeoPoint(12.0, 78.0);

        // Point offset purely along latitude by 0.0072 degrees
        // Theoretical distance: R * (0.0072 * PI / 180) = 6,371,000 * 0.0001256637... approx 800.6 m
        GeoPoint p = new GeoPoint(12.0072, 77.5);

        double dist = GeoMath.pointToSegmentMeters(p, a, b);

        // Acceptance: allow +/- 5 m around 800 m (795 m to 805 m)
        assertTrue(dist >= 795.0 && dist <= 805.0,
                "Distance should be within +/- 5m of 800m, but was: " + dist);

        double polyDist = GeoMath.pointToPolylineMeters(p, List.of(a, b));
        assertTrue(polyDist >= 795.0 && polyDist <= 805.0,
                "Polyline distance should be within +/- 5m of 800m, but was: " + polyDist);
    }

    @Test
    void beyondSegmentEndMeasuresDistanceToEndpointRatherThanInfiniteLine() {
        // Segment running east-west from lon 77.0 to 77.1 at lat 12.0
        GeoPoint a = new GeoPoint(12.0, 77.0);
        GeoPoint b = new GeoPoint(12.0, 77.1);

        // Point on the line extension past end point B (at lon 77.2)
        // If unconstrained/infinite line was used, distance would be 0 m.
        // Because t is clamped to 1, distance must be distance between P and B (0.1 deg lon).
        GeoPoint pastEnd = new GeoPoint(12.0, 77.2);
        double distPastEnd = GeoMath.pointToSegmentMeters(pastEnd, a, b);

        double expectedDistToEnd = GeoMath.pointToSegmentMeters(pastEnd, b, b);
        assertEquals(expectedDistToEnd, distPastEnd, 0.01);
        assertTrue(distPastEnd > 10_000.0, "Distance past end should be over 10km, was: " + distPastEnd);

        // Point on the line extension before start point A (at lon 76.9)
        GeoPoint beforeStart = new GeoPoint(12.0, 76.9);
        double distBeforeStart = GeoMath.pointToSegmentMeters(beforeStart, a, b);
        double expectedDistToStart = GeoMath.pointToSegmentMeters(beforeStart, a, a);
        assertEquals(expectedDistToStart, distBeforeStart, 0.01);
        assertTrue(distBeforeStart > 10_000.0, "Distance before start should be over 10km, was: " + distBeforeStart);
    }

    @Test
    void pointToPolylineWithMultipleSegmentsChoosesClosestSegment() {
        GeoPoint p0 = new GeoPoint(12.0, 77.0);
        GeoPoint p1 = new GeoPoint(12.0, 77.1);
        GeoPoint p2 = new GeoPoint(12.1, 77.1);

        List<GeoPoint> polyline = List.of(p0, p1, p2);

        // Test point near segment 1 (p1 -> p2)
        GeoPoint query = new GeoPoint(12.05, 77.1001);
        double dist = GeoMath.pointToPolylineMeters(query, polyline);

        // Query point is 0.0001 deg lon away from segment (p1 -> p2) at lat 12.05
        // Distance ~ 11 m
        assertTrue(dist > 0.0 && dist < 20.0, "Distance should be small near segment 1, was: " + dist);
    }

    @Test
    void pointToPolylineWithEmptyOrSinglePoint() {
        assertThrows(IllegalArgumentException.class, () -> GeoMath.pointToPolylineMeters(new GeoPoint(12.0, 77.0), List.of()));

        GeoPoint single = new GeoPoint(12.0, 77.0);
        GeoPoint target = new GeoPoint(12.0072, 77.0);
        double dist = GeoMath.pointToPolylineMeters(target, List.of(single));
        assertTrue(dist >= 795.0 && dist <= 805.0);
    }

    @Test
    void degenerateSegmentWhereStartEqualsEndReturnsDistanceToPoint() {
        GeoPoint a = new GeoPoint(12.0, 77.0);
        GeoPoint p = new GeoPoint(12.0072, 77.0);

        double dist = GeoMath.pointToSegmentMeters(p, a, a);
        assertTrue(dist >= 795.0 && dist <= 805.0);
    }
}

package com.example.tripaudit.detect;

import com.example.tripaudit.domain.GeoPoint;
import java.util.List;

/**
 * Geometric calculations on geographic coordinates using local flat-plane projection.
 *
 * All operations avoid external dependencies and Spring annotations.
 */
public final class GeoMath {

    /**
     * Mean Earth radius in metres (IUGG spherical Earth approximation: 6,371 km).
     */
    public static final double EARTH_RADIUS_METERS = 6_371_000.0;

    private GeoMath() {}

    public static double[] projectToLocalMeters(GeoPoint origin, GeoPoint target) {
        double latOriginRad = Math.toRadians(origin.lat());
        double dLatRad = Math.toRadians(target.lat() - origin.lat());
        double dLonRad = Math.toRadians(target.lon() - origin.lon());

        double x = EARTH_RADIUS_METERS * dLonRad * Math.cos(latOriginRad);
        double y = EARTH_RADIUS_METERS * dLatRad;
        return new double[]{x, y};
    }

    /**
     * Computes the distance in metres from point P to line segment A-B.
     *
     * Coordinates of A and B are projected onto a local flat plane centered at P (so P is at (0, 0)).
     * The projection parameter t = (AP . AB) / |AB|^2 is computed and clamped to [0, 1].
     * Clamping t prevents treating the finite segment as an infinite line, correctly measuring
     * distance to endpoints when P lies past segment boundaries.
     *
     * Time complexity: O(1)
     * Space complexity: O(1)
     *
     * @param p query point
     * @param a segment start point
     * @param b segment end point
     * @return perpendicular or clamped endpoint distance in metres
     */
    public static double pointToSegmentMeters(GeoPoint p, GeoPoint a, GeoPoint b) {
        double[] aLocal = projectToLocalMeters(p, a);
        double[] bLocal = projectToLocalMeters(p, b);

        double ax = aLocal[0];
        double ay = aLocal[1];
        double bx = bLocal[0];
        double by = bLocal[1];

        double abx = bx - ax;
        double aby = by - ay;
        double abLenSq = abx * abx + aby * aby;

        // Degenerate segment where A == B: distance is Euclidean distance from P(0, 0) to A
        if (abLenSq == 0.0) {
            return Math.hypot(ax, ay);
        }

        // Vector AP = P - A. Because P is at (0, 0), AP = (-ax, -ay).
        // Parameter t = (AP . AB) / |AB|^2
        double t = (-ax * abx - ay * aby) / abLenSq;

        // Clamp t to [0, 1] so we evaluate the line segment, not the infinite line
        if (t < 0.0) {
            t = 0.0;
        } else if (t > 1.0) {
            t = 1.0;
        }

        // Closest point on segment C = A + t * AB
        double cx = ax + t * abx;
        double cy = ay + t * aby;

        // Distance from P(0, 0) to C
        return Math.hypot(cx, cy);
    }

    /**
     * Computes the minimum distance in metres from point P to a polyline formed by an ordered list of vertices.
     *
     * Time complexity: O(S), where S is the number of segments (polyline.size() - 1).
     * Space complexity: O(1) auxiliary space.
     *
     * @param p query point
     * @param polyline ordered list of vertices representing the polyline
     * @return minimum distance in metres to the polyline
     */
    public static double pointToPolylineMeters(GeoPoint p, List<GeoPoint> polyline) {
        if (polyline == null || polyline.isEmpty()) {
            throw new IllegalArgumentException("Polyline must contain at least one point");
        }
        if (polyline.size() == 1) {
            double[] local = projectToLocalMeters(p, polyline.get(0));
            return Math.hypot(local[0], local[1]);
        }

        double minDistance = Double.POSITIVE_INFINITY;
        for (int i = 0; i < polyline.size() - 1; i++) {
            double d = pointToSegmentMeters(p, polyline.get(i), polyline.get(i + 1));
            if (d < minDistance) {
                minDistance = d;
            }
        }
        return minDistance;
    }
}

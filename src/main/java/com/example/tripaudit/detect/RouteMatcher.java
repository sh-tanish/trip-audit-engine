package com.example.tripaudit.detect;

import com.example.tripaudit.domain.GeoPoint;
import com.example.tripaudit.domain.RouteSegment;
import java.util.List;

/**
 * Finds the nearest route segment for a point among planned route segments.
 */
public final class RouteMatcher {

    private RouteMatcher() {}

    /**
     * Matches a GPS point to the nearest planned route segment.
     *
     * Iterates over all segments in the trip's planned route, finds the point-to-segment
     * distance via {@link GeoMath#pointToSegmentMeters}, and tracks the segment that produces
     * the minimal distance.
     *
     * Time complexity: O(S) where S is the number of segments in the route.
     * Space complexity: O(1) auxiliary space.
     *
     * @param point query GPS coordinate
     * @param segments list of route segments
     * @return SegmentMatch with the nearest segment index and distance in metres
     */
    public static SegmentMatch nearest(GeoPoint point, List<RouteSegment> segments) {
        if (segments == null || segments.isEmpty()) {
            throw new IllegalArgumentException("Segments list must not be null or empty");
        }

        int nearestIndex = -1;
        double minDistance = Double.POSITIVE_INFINITY;

        for (int i = 0; i < segments.size(); i++) {
            RouteSegment seg = segments.get(i);
            double dist = GeoMath.pointToSegmentMeters(point, seg.start(), seg.end());
            if (dist < minDistance) {
                minDistance = dist;
                nearestIndex = i;
            }
        }

        return new SegmentMatch(nearestIndex, minDistance);
    }
}

package com.example.tripaudit.detect;

/**
 * Result of matching a GPS fix to the planned route polyline.
 *
 * @param segmentIndex 0-based index of the nearest route segment
 * @param distanceMeters distance in metres from the fix to that nearest segment
 */
public record SegmentMatch(int segmentIndex, double distanceMeters) { }

package com.example.tripaudit.rules;

import com.example.tripaudit.detect.SegmentMatch;
import com.example.tripaudit.domain.GpsFix;
import java.time.Instant;

/**
 * Immutable pair of a cleaned GPS fix and its pre-computed nearest route segment.
 * Allows rules (route deviation, over-speed) to evaluate sustained conditions without
 * recomputing point-to-polyline projections.
 */
public record MatchedFix(GpsFix fix, SegmentMatch match) {

    public Instant recordedAt() {
        return fix.getRecordedAt();
    }

    public double speedKmph() {
        return fix.getSpeedKmph();
    }

    public double distanceMeters() {
        return match.distanceMeters();
    }

    public int segmentIndex() {
        return match.segmentIndex();
    }
}

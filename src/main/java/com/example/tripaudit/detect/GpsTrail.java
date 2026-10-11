package com.example.tripaudit.detect;

import com.example.tripaudit.domain.GpsFix;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Normalization utilities for GPS fix trails.
 */
public final class GpsTrail {

    private GpsTrail() {}

    /**
     *
     * @param fixes raw list of GPS fixes, which may be unsorted or contain duplicates
     * @return clean, unmodifiable list of chronological GPS fixes without duplicate timestamps
     */
    public static List<GpsFix> normalize(List<GpsFix> fixes) {
        if (fixes == null || fixes.isEmpty()) {
            return List.of();
        }

        List<GpsFix> sorted = new ArrayList<>(fixes);
        sorted.sort(Comparator.comparing(GpsFix::getRecordedAt));

        List<GpsFix> cleaned = new ArrayList<>(sorted.size());
        cleaned.add(sorted.get(0));

        for (int i = 1; i < sorted.size(); i++) {
            GpsFix current = sorted.get(i);
            GpsFix previous = cleaned.get(cleaned.size() - 1);
            // Drop duplicate timestamps (keep the first occurrence)
            if (!current.getRecordedAt().equals(previous.getRecordedAt())) {
                cleaned.add(current);
            }
        }

        return List.copyOf(cleaned);
    }
}

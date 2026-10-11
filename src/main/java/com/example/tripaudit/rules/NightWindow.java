package com.example.tripaudit.rules;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;

/**
 * Value object representing a daily time window [start, end).
 *
 * Supports windows that cross midnight (e.g., 22:00 to 06:00).
 * If start == end, the window is considered empty (disabled).
 */
public record NightWindow(LocalTime start, LocalTime end) {

    /**
     * Checks if the clock time {@code t} falls within the window [start, end).
     *
     * @param t local clock time
     * @return true if t is inside the window
     */
    public boolean contains(LocalTime t) {
        if (t == null || start == null || end == null || start.equals(end)) {
            return false;
        }

        if (start.isBefore(end)) {
            // Normal daytime window (e.g. 09:00 to 17:00)
            return !t.isBefore(start) && t.isBefore(end);
        } else {
            // Midnight-crossing window (e.g. 22:00 to 06:00)
            return !t.isBefore(start) || t.isBefore(end);
        }
    }

    /**
     * Checks if the UTC instant falls within the night window when converted to the given time zone.
     *
     * @param instant timestamp
     * @param zone target time zone
     * @return true if inside the night window
     */
    public boolean contains(Instant instant, ZoneId zone) {
        if (instant == null || zone == null) {
            return false;
        }
        LocalTime t = instant.atZone(zone).toLocalTime();
        return contains(t);
    }
}

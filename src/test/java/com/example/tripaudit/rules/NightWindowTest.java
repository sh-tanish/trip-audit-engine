package com.example.tripaudit.rules;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NightWindowTest {

    private final ZoneId zone = ZoneId.of("Asia/Kolkata");

    @Test
    @DisplayName("Night window boundaries: 22:00 inside, 06:00 outside, crossing midnight")
    void testBoundariesCrossingMidnight() {
        NightWindow window = new NightWindow(LocalTime.of(22, 0), LocalTime.of(6, 0));

        // Exactly at start (22:00) -> inside
        assertThat(window.contains(LocalTime.of(22, 0))).isTrue();
        // 23:10 -> inside
        assertThat(window.contains(LocalTime.of(23, 10))).isTrue();
        // Midnight (00:00) -> inside
        assertThat(window.contains(LocalTime.of(0, 0))).isTrue();
        // 01:30 -> inside
        assertThat(window.contains(LocalTime.of(1, 30))).isTrue();
        // 05:59:59 -> inside
        assertThat(window.contains(LocalTime.of(5, 59, 59))).isTrue();

        // Exactly at end (06:00) -> outside
        assertThat(window.contains(LocalTime.of(6, 0))).isFalse();
        // 06:01 -> outside
        assertThat(window.contains(LocalTime.of(6, 1))).isFalse();
        // 12:00 -> outside
        assertThat(window.contains(LocalTime.of(12, 0))).isFalse();
        // 21:59 -> outside
        assertThat(window.contains(LocalTime.of(21, 59))).isFalse();
    }

    @Test
    @DisplayName("start == end disables the window (empty window)")
    void testDisabledWindowWhenStartEqualsEnd() {
        NightWindow window = new NightWindow(LocalTime.of(22, 0), LocalTime.of(22, 0));

        assertThat(window.contains(LocalTime.of(22, 0))).isFalse();
        assertThat(window.contains(LocalTime.of(23, 0))).isFalse();
        assertThat(window.contains(LocalTime.of(10, 0))).isFalse();
    }

    @Test
    @DisplayName("Normal daytime window (start < end)")
    void testDaytimeWindow() {
        NightWindow window = new NightWindow(LocalTime.of(9, 0), LocalTime.of(17, 0));

        assertThat(window.contains(LocalTime.of(9, 0))).isTrue();
        assertThat(window.contains(LocalTime.of(13, 0))).isTrue();
        assertThat(window.contains(LocalTime.of(16, 59))).isTrue();
        assertThat(window.contains(LocalTime.of(17, 0))).isFalse();
        assertThat(window.contains(LocalTime.of(8, 59))).isFalse();
    }

    @Test
    @DisplayName("Instant conversion with timezone")
    void testInstantWithTimezone() {
        NightWindow window = new NightWindow(LocalTime.of(22, 0), LocalTime.of(6, 0));
        // 2026-10-11T23:10:00+05:30 in UTC is 2026-10-11T17:40:00Z
        Instant instant = Instant.parse("2026-10-11T17:40:00Z");

        assertThat(window.contains(instant, zone)).isTrue();
    }
}

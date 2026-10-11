package com.example.tripaudit.config;

import java.time.Duration;
import java.time.LocalTime;
import java.time.ZoneId;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Strongly-typed configuration thresholds for safety audit rules.
 * Bound from application.yml under the "audit" prefix.
 */
@ConfigurationProperties(prefix = "audit")
public record AuditProperties(
        ZoneId zone,
        Night night,
        Route route,
        Speed speed,
        Gps gps
) {
    public record Night(LocalTime start, LocalTime end) { }
    public record Route(double thresholdMeters, Duration minDuration) { }
    public record Speed(Duration minDuration, double criticalFactor) { }
    public record Gps(Duration maxGap) { }
}

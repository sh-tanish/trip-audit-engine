package com.example.tripaudit.alert;

import com.example.tripaudit.domain.AlertChannel;
import com.example.tripaudit.domain.Severity;
import java.util.Collections;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Maps severity levels to designated notification channels:
 * - CRITICAL: CONTROL_ROOM + TRANSPORT_MANAGER (immediate escalation)
 * - HIGH: TRANSPORT_MANAGER (manager intervention required)
 * - MEDIUM: DASHBOARD (daily digest / dashboard warning)
 */
@Component
public class AlertRoutingPolicy implements AlertRouter, SeverityPolicy {

    private static final Set<AlertChannel> CRITICAL_CHANNELS = Set.of(
            AlertChannel.CONTROL_ROOM,
            AlertChannel.TRANSPORT_MANAGER
    );

    private static final Set<AlertChannel> HIGH_CHANNELS = Set.of(
            AlertChannel.TRANSPORT_MANAGER
    );

    private static final Set<AlertChannel> MEDIUM_CHANNELS = Set.of(
            AlertChannel.DASHBOARD
    );

    @Override
    public Set<AlertChannel> route(Severity severity) {
        if (severity == null) {
            return Collections.emptySet();
        }
        return switch (severity) {
            case CRITICAL -> CRITICAL_CHANNELS;
            case HIGH -> HIGH_CHANNELS;
            case MEDIUM -> MEDIUM_CHANNELS;
        };
    }
}

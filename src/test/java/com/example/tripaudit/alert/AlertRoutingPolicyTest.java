package com.example.tripaudit.alert;

import com.example.tripaudit.domain.AlertChannel;
import com.example.tripaudit.domain.Severity;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AlertRoutingPolicyTest {

    private final AlertRoutingPolicy policy = new AlertRoutingPolicy();

    @Test
    @DisplayName("CRITICAL routes to CONTROL_ROOM and TRANSPORT_MANAGER")
    void criticalRoutesToControlRoomAndTransportManager() {
        Set<AlertChannel> channels = policy.route(Severity.CRITICAL);
        assertEquals(Set.of(AlertChannel.CONTROL_ROOM, AlertChannel.TRANSPORT_MANAGER), channels);
    }

    @Test
    @DisplayName("HIGH routes only to TRANSPORT_MANAGER")
    void highRoutesToTransportManagerOnly() {
        Set<AlertChannel> channels = policy.route(Severity.HIGH);
        assertEquals(Set.of(AlertChannel.TRANSPORT_MANAGER), channels);
    }

    @Test
    @DisplayName("MEDIUM routes only to DASHBOARD")
    void mediumRoutesToDashboardOnly() {
        Set<AlertChannel> channels = policy.route(Severity.MEDIUM);
        assertEquals(Set.of(AlertChannel.DASHBOARD), channels);
    }

    @Test
    @DisplayName("null severity returns empty set")
    void nullSeverityReturnsEmptySet() {
        Set<AlertChannel> channels = policy.route(null);
        assertTrue(channels.isEmpty());
    }
}

package com.example.tripaudit.alert;

import com.example.tripaudit.domain.AlertChannel;
import com.example.tripaudit.domain.Severity;
import com.example.tripaudit.domain.ViolationType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class PrivacyLeakSweepTest {

    @Test
    @DisplayName("AlertMessage clears coordinates for any violation type other than SOS")
    void nonSosAlertMessageClearsCoordinates() {
        // Attempt to supply GPS coordinates to non-SOS violations
        AlertMessage night = new AlertMessage(1L, 100L, ViolationType.NIGHT_SAFETY, Severity.HIGH, "Night alert", 12.9716, 77.5946);
        assertNull(night.lat(), "NIGHT_SAFETY must not carry latitude");
        assertNull(night.lon(), "NIGHT_SAFETY must not carry longitude");

        AlertMessage route = new AlertMessage(2L, 100L, ViolationType.ROUTE_DEVIATION, Severity.HIGH, "Route alert", 12.9716, 77.5946);
        assertNull(route.lat(), "ROUTE_DEVIATION must not carry latitude");
        assertNull(route.lon(), "ROUTE_DEVIATION must not carry longitude");

        AlertMessage speed = new AlertMessage(3L, 100L, ViolationType.OVER_SPEED, Severity.MEDIUM, "Speed alert", 12.9716, 77.5946);
        assertNull(speed.lat(), "OVER_SPEED must not carry latitude");
        assertNull(speed.lon(), "OVER_SPEED must not carry longitude");

        AlertMessage otp = new AlertMessage(4L, 100L, ViolationType.OTP_MISMATCH, Severity.HIGH, "OTP alert", 12.9716, 77.5946);
        assertNull(otp.lat(), "OTP_MISMATCH must not carry latitude");
        assertNull(otp.lon(), "OTP_MISMATCH must not carry longitude");

        // SOS alert DOES retain coordinates
        AlertMessage sos = new AlertMessage(5L, 100L, ViolationType.SOS, Severity.CRITICAL, "SOS alert", 12.9716, 77.5946);
        assertNotNull(sos.lat(), "SOS must retain latitude");
        assertNotNull(sos.lon(), "SOS must retain longitude");
        assertEquals(12.9716, sos.lat(), 1e-4);
        assertEquals(77.5946, sos.lon(), 1e-4);
    }
}

package com.example.tripaudit.alert;

import com.example.tripaudit.domain.AlertChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Log stub for CONTROL_ROOM alerts (SMS notification channel).
 * Logs sanitized alert facts without employee phone numbers or personal data.
 */
@Component
public class SmsNotifierStub implements Notifier {

    private static final Logger log = LoggerFactory.getLogger(SmsNotifierStub.class);

    @Override
    public AlertChannel channel() {
        return AlertChannel.CONTROL_ROOM;
    }

    @Override
    public void send(AlertMessage msg) {
        if (msg.lat() != null && msg.lon() != null) {
            log.info("[SMS -> CONTROL_ROOM] SOS Emergency! Trip: {}, Violation: {}, Severity: {}, Summary: {}, Location: ({}, {})",
                    msg.tripId(), msg.violationId(), msg.severity(), msg.summary(), msg.lat(), msg.lon());
        } else {
            log.info("[SMS -> CONTROL_ROOM] Alert for Trip: {}, Violation: {}, Severity: {}, Summary: {}",
                    msg.tripId(), msg.violationId(), msg.severity(), msg.summary());
        }
    }
}

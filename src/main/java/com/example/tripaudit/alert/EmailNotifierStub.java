package com.example.tripaudit.alert;

import com.example.tripaudit.domain.AlertChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Log stub for TRANSPORT_MANAGER alerts (Email notification channel).
 * Logs sanitized alert facts without recipient email addresses or personal data.
 */
@Component
public class EmailNotifierStub implements Notifier {

    private static final Logger log = LoggerFactory.getLogger(EmailNotifierStub.class);

    @Override
    public AlertChannel channel() {
        return AlertChannel.TRANSPORT_MANAGER;
    }

    @Override
    public void send(AlertMessage msg) {
        log.info("[EMAIL -> TRANSPORT_MANAGER] Alert for Trip: {}, Violation: {}, Severity: {}, Summary: {}",
                msg.tripId(), msg.violationId(), msg.severity(), msg.summary());
    }
}

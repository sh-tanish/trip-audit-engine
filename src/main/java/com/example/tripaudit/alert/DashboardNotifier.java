package com.example.tripaudit.alert;

import com.example.tripaudit.domain.AlertChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Log stub for DASHBOARD warnings and digests.
 */
@Component
public class DashboardNotifier implements Notifier {

    private static final Logger log = LoggerFactory.getLogger(DashboardNotifier.class);

    @Override
    public AlertChannel channel() {
        return AlertChannel.DASHBOARD;
    }

    @Override
    public void send(AlertMessage msg) {
        log.info("[DASHBOARD] Warning for Trip: {}, Violation: {}, Severity: {}, Summary: {}",
                msg.tripId(), msg.violationId(), msg.severity(), msg.summary());
    }
}

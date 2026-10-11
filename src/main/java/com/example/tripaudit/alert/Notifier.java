package com.example.tripaudit.alert;

import com.example.tripaudit.domain.AlertChannel;

/**
 * Strategy interface for delivering safety alerts to a specific channel.
 */
public interface Notifier {

    /**
     * The alert channel handled by this notifier.
     */
    AlertChannel channel();

    /**
     * Sends an alert notification. Implementations must avoid logging personal data.
     *
     * @param msg sanitized alert payload
     */
    void send(AlertMessage msg);
}

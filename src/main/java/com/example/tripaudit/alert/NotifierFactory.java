package com.example.tripaudit.alert;

import com.example.tripaudit.domain.AlertChannel;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Factory for looking up Notifier strategy implementations by AlertChannel.
 * Constructed via Spring's auto-injection of all Notifier beans.
 */
@Component
public class NotifierFactory {

    private final Map<AlertChannel, Notifier> notifiers = new ConcurrentHashMap<>();

    public NotifierFactory(List<Notifier> notifierList) {
        for (Notifier notifier : notifierList) {
            notifiers.put(notifier.channel(), notifier);
        }
    }

    /**
     * Registers or overrides a Notifier implementation (e.g. for testing).
     */
    public void register(Notifier notifier) {
        notifiers.put(notifier.channel(), notifier);
    }

    /**
     * Resolves the Notifier for the specified channel.
     *
     * @param channel notification channel
     * @return Notifier registered for the channel
     * @throws IllegalArgumentException if no notifier is configured for the channel
     */
    public Notifier forChannel(AlertChannel channel) {
        Notifier notifier = notifiers.get(channel);
        if (notifier == null) {
            throw new IllegalArgumentException("No notifier registered for channel: " + channel);
        }
        return notifier;
    }
}

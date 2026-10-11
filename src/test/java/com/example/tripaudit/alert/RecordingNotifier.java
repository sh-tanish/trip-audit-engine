package com.example.tripaudit.alert;

import com.example.tripaudit.domain.AlertChannel;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Thread-safe test Notifier that records all dispatched AlertMessage objects.
 */
public class RecordingNotifier implements Notifier {

    private final AlertChannel channel;
    private final List<AlertMessage> recordedMessages = new CopyOnWriteArrayList<>();

    public RecordingNotifier(AlertChannel channel) {
        this.channel = channel;
    }

    @Override
    public AlertChannel channel() {
        return channel;
    }

    @Override
    public void send(AlertMessage msg) {
        recordedMessages.add(msg);
    }

    public List<AlertMessage> getRecordedMessages() {
        return Collections.unmodifiableList(recordedMessages);
    }

    public int getSendCount() {
        return recordedMessages.size();
    }

    public void clear() {
        recordedMessages.clear();
    }
}

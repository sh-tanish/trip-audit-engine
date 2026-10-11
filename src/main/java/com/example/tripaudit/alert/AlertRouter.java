package com.example.tripaudit.alert;

import com.example.tripaudit.domain.AlertChannel;
import com.example.tripaudit.domain.Severity;
import java.util.Set;

/**
 * Strategy interface for routing safety violations to alert channels based on severity.
 */
public interface AlertRouter {
    Set<AlertChannel> route(Severity severity);
}

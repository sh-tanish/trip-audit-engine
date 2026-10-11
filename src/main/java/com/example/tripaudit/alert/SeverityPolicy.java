package com.example.tripaudit.alert;

import com.example.tripaudit.domain.AlertChannel;
import com.example.tripaudit.domain.Severity;
import java.util.Set;

/**
 * Strategy interface defining alert channel mapping for each severity level.
 */
public interface SeverityPolicy {
    Set<AlertChannel> route(Severity severity);
}

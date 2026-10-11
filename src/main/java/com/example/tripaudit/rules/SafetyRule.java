package com.example.tripaudit.rules;

import com.example.tripaudit.domain.Violation;
import java.util.List;

/**
 * Strategy interface for trip safety and compliance audit rules.
 *
 * Rules are pure functions: TripContext -> list of unsaved Violation objects.
 * A rule never touches the database, never sends alerts, and never logs PII.
 */
public interface SafetyRule {

    /**
     * Evaluates the trip context and returns any newly detected, unsaved violations.
     *
     * @param ctx immutable snapshot of the trip and its associated facts
     * @return list of new, unsaved Violation instances (empty if compliant)
     */
    List<Violation> evaluate(TripContext ctx);
}

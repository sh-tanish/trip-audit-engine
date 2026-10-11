package com.example.tripaudit.domain;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import org.hibernate.annotations.Immutable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ViolationImmutabilityTest {

    @Test
    @DisplayName("Violation class must have no setter methods")
    void violationHasNoSetters() {
        Method[] declaredMethods = Violation.class.getDeclaredMethods();
        List<Method> setters = Arrays.stream(declaredMethods)
                .filter(m -> m.getName().startsWith("set"))
                .toList();

        assertTrue(setters.isEmpty(),
                "Violation must be append-only and have NO setter methods, but found: " + setters);
    }

    @Test
    @DisplayName("Violation class must be annotated with Hibernate @Immutable")
    void violationHasImmutableAnnotation() {
        assertTrue(Violation.class.isAnnotationPresent(Immutable.class),
                "Violation must be annotated with @org.hibernate.annotations.Immutable");
    }

    @Test
    @DisplayName("Violation constructor must derive dedupKey automatically")
    void constructorDerivesDedupKey() {
        Violation v = new Violation(
                100L,
                ViolationType.NIGHT_SAFETY,
                Severity.HIGH,
                "NightSafetyRule",
                "stop-10",
                10L,
                20L,
                "Last drop alone at night",
                java.time.Instant.now(),
                "run-123"
        );

        assertNotNull(v.getDedupKey(), "dedupKey must not be null");
        assertEquals(64, v.getDedupKey().length(), "dedupKey must be a 64-char hex SHA-256");
        assertEquals(v.getTripId(), 100L);
        assertEquals(v.getType(), ViolationType.NIGHT_SAFETY);
        assertEquals(v.getSeverity(), Severity.HIGH);
    }
}

package com.example.tripaudit.repository;

import com.example.tripaudit.domain.Severity;
import com.example.tripaudit.domain.Violation;
import com.example.tripaudit.domain.ViolationType;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class ViolationRepositoryDuplicateDedupKeyTest {

    @Autowired
    private ViolationRepository violationRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("Saving two violations with identical dedup_key must be rejected with DataIntegrityViolationException")
    void duplicateDedupKeyIsRejected() {
        Instant now = Instant.now();

        // v1 and v2 have identical (tripId=999, type=NIGHT_SAFETY, subjectKey="stop-1") -> same dedup_key
        Violation v1 = new Violation(999L, ViolationType.NIGHT_SAFETY, Severity.HIGH,
                "NightSafetyRule", "stop-1", 1L, 10L, "First violation evidence", now, "run-1");

        Violation v2 = new Violation(999L, ViolationType.NIGHT_SAFETY, Severity.HIGH,
                "NightSafetyRule", "stop-1", 1L, 10L, "Duplicate violation evidence", now, "run-2");

        assertEquals(v1.getDedupKey(), v2.getDedupKey(), "Both violations must have identical derived dedupKey");

        // Save first violation
        violationRepository.save(v1);
        entityManager.flush();

        assertTrue(violationRepository.existsByDedupKey(v1.getDedupKey()),
                "existsByDedupKey must return true after inserting");

        // Save duplicate violation: database UNIQUE constraint uk_violation_dedup_key must reject it
        assertThrows(DataIntegrityViolationException.class, () -> {
            violationRepository.save(v2);
        }, "Inserting a violation with existing dedup_key must fail unique constraint");
    }

    @Test
    @DisplayName("existsByDedupKey returns false for non-existent key")
    void existsByDedupKeyFalseForUnknown() {
        assertFalse(violationRepository.existsByDedupKey("non-existent-dedup-key-64chars"));
    }
}

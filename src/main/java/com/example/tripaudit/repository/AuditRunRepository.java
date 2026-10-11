package com.example.tripaudit.repository;

import com.example.tripaudit.domain.AuditRun;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditRunRepository extends JpaRepository<AuditRun, String> {
    List<AuditRun> findByTripIdOrderByStartedAtDesc(Long tripId);
}

package com.example.tripaudit.repository;

import com.example.tripaudit.domain.GpsFix;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GpsFixRepository extends JpaRepository<GpsFix, Long> {
    List<GpsFix> findByTripIdOrderByRecordedAtAsc(Long tripId);
    Optional<GpsFix> findFirstByTripIdAndRecordedAtLessThanEqualOrderByRecordedAtDesc(Long tripId, Instant recordedAt);
}


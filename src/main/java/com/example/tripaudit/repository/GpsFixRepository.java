package com.example.tripaudit.repository;

import com.example.tripaudit.domain.GpsFix;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GpsFixRepository extends JpaRepository<GpsFix, Long> {
    List<GpsFix> findByTripIdOrderByRecordedAtAsc(Long tripId);
}

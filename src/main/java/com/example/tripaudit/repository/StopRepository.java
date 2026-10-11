package com.example.tripaudit.repository;

import com.example.tripaudit.domain.Stop;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StopRepository extends JpaRepository<Stop, Long> {
    List<Stop> findByTripIdOrderBySequenceAsc(Long tripId);
}

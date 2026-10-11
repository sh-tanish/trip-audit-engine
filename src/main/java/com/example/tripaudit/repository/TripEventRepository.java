package com.example.tripaudit.repository;

import com.example.tripaudit.domain.TripEvent;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TripEventRepository extends JpaRepository<TripEvent, Long> {
    List<TripEvent> findByTripIdOrderByOccurredAtAsc(Long tripId);
}

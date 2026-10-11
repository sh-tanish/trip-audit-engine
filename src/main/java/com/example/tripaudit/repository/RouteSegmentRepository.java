package com.example.tripaudit.repository;

import com.example.tripaudit.domain.RouteSegment;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RouteSegmentRepository extends JpaRepository<RouteSegment, Long> {
    List<RouteSegment> findByTripIdOrderBySeqAsc(Long tripId);
}

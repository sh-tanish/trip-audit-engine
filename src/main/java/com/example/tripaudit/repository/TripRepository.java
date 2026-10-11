package com.example.tripaudit.repository;

import com.example.tripaudit.domain.Trip;
import com.example.tripaudit.domain.TripStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TripRepository extends JpaRepository<Trip, Long> {
    List<Trip> findByStatus(TripStatus status);
}

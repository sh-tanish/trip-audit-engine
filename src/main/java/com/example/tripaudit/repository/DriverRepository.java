package com.example.tripaudit.repository;

import com.example.tripaudit.domain.Driver;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DriverRepository extends JpaRepository<Driver, Long> {
    Optional<Driver> findByDriverCode(String driverCode);
}

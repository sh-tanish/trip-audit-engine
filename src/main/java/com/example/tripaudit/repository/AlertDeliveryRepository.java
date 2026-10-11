package com.example.tripaudit.repository;

import com.example.tripaudit.domain.AlertDelivery;
import com.example.tripaudit.domain.DeliveryStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AlertDeliveryRepository extends JpaRepository<AlertDelivery, Long> {
    List<AlertDelivery> findByViolationId(Long violationId);
    List<AlertDelivery> findByStatus(DeliveryStatus status);
}

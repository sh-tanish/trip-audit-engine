package com.example.tripaudit.api;

import com.example.tripaudit.api.dto.AlertDeliveryResponse;
import com.example.tripaudit.api.dto.ViolationResponse;
import com.example.tripaudit.domain.AlertDelivery;
import com.example.tripaudit.domain.Violation;
import com.example.tripaudit.service.TripService;
import com.example.tripaudit.service.TripService.ViolationDetail;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class ViolationController {

    private final TripService tripService;

    public ViolationController(TripService tripService) {
        this.tripService = tripService;
    }

    @GetMapping("/trips/{id}/violations")
    public ResponseEntity<List<ViolationResponse>> getTripViolations(@PathVariable Long id) {
        List<Violation> violations = tripService.getTripViolations(id);
        List<ViolationResponse> response = violations.stream()
                .map(v -> toResponse(v, null))
                .toList();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/violations/{id}")
    public ResponseEntity<ViolationResponse> getViolationById(@PathVariable Long id) {
        ViolationDetail detail = tripService.getViolation(id);
        List<AlertDeliveryResponse> deliveries = detail.deliveries().stream()
                .map(this::toDeliveryResponse)
                .toList();
        return ResponseEntity.ok(toResponse(detail.violation(), deliveries));
    }

    private ViolationResponse toResponse(Violation v, List<AlertDeliveryResponse> deliveries) {
        return new ViolationResponse(
                v.getId(),
                v.getDedupKey(),
                v.getTripId(),
                v.getType(),
                v.getSeverity(),
                v.getRuleName(),
                v.getSubjectKey(),
                v.getStopId(),
                v.getEmployeeId(),
                v.getEvidence(),
                v.getDetectedAt(),
                v.getAuditRunId(),
                deliveries
        );
    }

    private AlertDeliveryResponse toDeliveryResponse(AlertDelivery d) {
        return new AlertDeliveryResponse(
                d.getId(),
                d.getChannel(),
                d.getStatus(),
                d.getAttempts(),
                d.getLastError(),
                d.getUpdatedAt()
        );
    }
}

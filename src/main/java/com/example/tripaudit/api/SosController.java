package com.example.tripaudit.api;

import com.example.tripaudit.api.dto.SosRequest;
import com.example.tripaudit.api.dto.ViolationResponse;
import com.example.tripaudit.domain.Violation;
import com.example.tripaudit.service.SosService;
import com.example.tripaudit.service.SosService.SosResult;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/trips/{id}/sos")
public class SosController {

    private final SosService sosService;

    public SosController(SosService sosService) {
        this.sosService = sosService;
    }

    @PostMapping
    public ResponseEntity<ViolationResponse> handleSos(
            @PathVariable Long id,
            @Valid @RequestBody SosRequest request) {
        SosResult result = sosService.processSos(id, request.employeeId(), request.pressedAt());
        HttpStatus status = result.isNew() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(toResponse(result.violation()));
    }

    private ViolationResponse toResponse(Violation v) {
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
                null
        );
    }
}

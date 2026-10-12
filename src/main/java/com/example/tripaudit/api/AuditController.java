package com.example.tripaudit.api;

import com.example.tripaudit.api.dto.AuditRunResponse;
import com.example.tripaudit.domain.AuditRun;
import com.example.tripaudit.service.AuditService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/trips/{id}/audit")
public class AuditController {

    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    @PostMapping
    public ResponseEntity<AuditRunResponse> auditTrip(@PathVariable Long id) {
        AuditRun auditRun = auditService.auditTrip(id, "ADMIN");
        AuditRunResponse response = new AuditRunResponse(
                auditRun.getId(),
                auditRun.getTripId(),
                auditRun.getKind(),
                auditRun.getTriggeredBy(),
                auditRun.getStartedAt(),
                auditRun.getFinishedAt(),
                auditRun.getNewViolations()
        );
        return ResponseEntity.ok(response);
    }
}

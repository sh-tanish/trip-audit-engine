package com.example.tripaudit.api.dto;

import com.example.tripaudit.domain.StopStatus;
import com.example.tripaudit.domain.StopType;
import java.time.Instant;

/**
 * Public representation of a Stop. Contains the generated stop ID, but deliberately
 * omits expected OTP or any OTP details.
 */
public record StopResponse(
        Long id,
        int sequence,
        StopType type,
        Long employeeId,
        String employeeCode,
        Instant plannedTime,
        StopStatus status
) {}

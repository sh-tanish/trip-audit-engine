package com.example.tripaudit.api.dto;

import jakarta.validation.constraints.NotBlank;

public record VehicleRequest(
        @NotBlank String registrationNumber
) {}

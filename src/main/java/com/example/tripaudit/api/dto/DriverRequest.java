package com.example.tripaudit.api.dto;

import jakarta.validation.constraints.NotBlank;

public record DriverRequest(
        @NotBlank String driverCode,
        @NotBlank String name
) {}

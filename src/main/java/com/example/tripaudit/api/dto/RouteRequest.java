package com.example.tripaudit.api.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record RouteRequest(
        @NotNull @Size(min = 2) List<List<Double>> points,
        @NotNull @NotEmpty List<Double> speedLimitsKmph
) {}

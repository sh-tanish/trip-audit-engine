package com.example.tripaudit.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record GpsBatchRequest(
        @NotEmpty @Size(max = 5000) List<@Valid GpsFixRequest> fixes
) {}

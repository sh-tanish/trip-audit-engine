package com.example.tripaudit.api.dto;

import com.example.tripaudit.domain.Gender;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record EmployeeRequest(
        @NotBlank String employeeCode,
        @NotNull Gender gender
) {}

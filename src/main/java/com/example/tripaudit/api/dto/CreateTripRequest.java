package com.example.tripaudit.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record CreateTripRequest(
        @NotNull @Valid DriverRequest driver,
        @NotNull @Valid VehicleRequest vehicle,
        @NotNull Boolean escortAssigned,
        @NotEmpty List<@Valid EmployeeRequest> employees,
        @NotEmpty List<@Valid StopRequest> stops,
        @NotNull @Valid RouteRequest route
) {}

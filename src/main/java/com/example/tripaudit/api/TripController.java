package com.example.tripaudit.api;

import com.example.tripaudit.api.dto.CreateTripRequest;
import com.example.tripaudit.api.dto.GpsBatchRequest;
import com.example.tripaudit.api.dto.GpsBatchResponse;
import com.example.tripaudit.api.dto.TripEventRequest;
import com.example.tripaudit.api.dto.TripEventResponse;
import com.example.tripaudit.api.dto.TripResponse;
import com.example.tripaudit.service.TripService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/trips")
public class TripController {

    private final TripService tripService;

    public TripController(TripService tripService) {
        this.tripService = tripService;
    }

    @PostMapping
    public ResponseEntity<TripResponse> createTrip(@Valid @RequestBody CreateTripRequest request) {
        TripResponse response = tripService.createTrip(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{id}/gps")
    public ResponseEntity<GpsBatchResponse> ingestGps(
            @PathVariable Long id,
            @Valid @RequestBody GpsBatchRequest request) {
        GpsBatchResponse response = tripService.ingestGps(id, request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/events")
    public ResponseEntity<TripEventResponse> recordEvent(
            @PathVariable Long id,
            @Valid @RequestBody TripEventRequest request) {
        TripEventResponse response = tripService.recordEvent(id, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}

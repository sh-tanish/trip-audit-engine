package com.example.tripaudit.service;

import com.example.tripaudit.api.dto.CreateTripRequest;
import com.example.tripaudit.api.dto.EmployeeRequest;
import com.example.tripaudit.api.dto.GpsBatchRequest;
import com.example.tripaudit.api.dto.GpsBatchResponse;
import com.example.tripaudit.api.dto.GpsFixRequest;
import com.example.tripaudit.api.dto.RouteRequest;
import com.example.tripaudit.api.dto.StopRequest;
import com.example.tripaudit.api.dto.StopResponse;
import com.example.tripaudit.api.dto.TripEventRequest;
import com.example.tripaudit.api.dto.TripEventResponse;
import com.example.tripaudit.api.dto.TripResponse;
import com.example.tripaudit.domain.AlertDelivery;
import com.example.tripaudit.domain.Driver;
import com.example.tripaudit.domain.Employee;
import com.example.tripaudit.domain.GeoPoint;
import com.example.tripaudit.domain.GpsFix;
import com.example.tripaudit.domain.RouteSegment;
import com.example.tripaudit.domain.Stop;
import com.example.tripaudit.domain.StopStatus;
import com.example.tripaudit.domain.StopType;
import com.example.tripaudit.domain.Trip;
import com.example.tripaudit.domain.TripEvent;
import com.example.tripaudit.domain.TripEventType;
import com.example.tripaudit.domain.TripStatus;
import com.example.tripaudit.domain.Vehicle;
import com.example.tripaudit.domain.Violation;
import com.example.tripaudit.exception.ConflictException;
import com.example.tripaudit.exception.NotFoundException;
import com.example.tripaudit.repository.AlertDeliveryRepository;
import com.example.tripaudit.repository.DriverRepository;
import com.example.tripaudit.repository.EmployeeRepository;
import com.example.tripaudit.repository.GpsFixRepository;
import com.example.tripaudit.repository.RouteSegmentRepository;
import com.example.tripaudit.repository.StopRepository;
import com.example.tripaudit.repository.TripEventRepository;
import com.example.tripaudit.repository.TripRepository;
import com.example.tripaudit.repository.VehicleRepository;
import com.example.tripaudit.repository.ViolationRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TripService {

    private final TripRepository tripRepository;
    private final StopRepository stopRepository;
    private final RouteSegmentRepository routeSegmentRepository;
    private final GpsFixRepository gpsFixRepository;
    private final TripEventRepository tripEventRepository;
    private final DriverRepository driverRepository;
    private final VehicleRepository vehicleRepository;
    private final EmployeeRepository employeeRepository;
    private final ViolationRepository violationRepository;
    private final AlertDeliveryRepository alertDeliveryRepository;
    private final OtpVerifier otpVerifier;

    public TripService(TripRepository tripRepository,
                       StopRepository stopRepository,
                       RouteSegmentRepository routeSegmentRepository,
                       GpsFixRepository gpsFixRepository,
                       TripEventRepository tripEventRepository,
                       DriverRepository driverRepository,
                       VehicleRepository vehicleRepository,
                       EmployeeRepository employeeRepository,
                       ViolationRepository violationRepository,
                       AlertDeliveryRepository alertDeliveryRepository,
                       OtpVerifier otpVerifier) {
        this.tripRepository = tripRepository;
        this.stopRepository = stopRepository;
        this.routeSegmentRepository = routeSegmentRepository;
        this.gpsFixRepository = gpsFixRepository;
        this.tripEventRepository = tripEventRepository;
        this.driverRepository = driverRepository;
        this.vehicleRepository = vehicleRepository;
        this.employeeRepository = employeeRepository;
        this.violationRepository = violationRepository;
        this.alertDeliveryRepository = alertDeliveryRepository;
        this.otpVerifier = otpVerifier;
    }

    /**
     * Creates a new trip with stops, route segments, and find-or-creates driver/vehicle/employees.
     */
    @Transactional
    public TripResponse createTrip(CreateTripRequest request) {
        validateTripRequest(request);

        // 1. Find or create Driver
        Driver driver = driverRepository.findByDriverCode(request.driver().driverCode())
                .orElseGet(() -> driverRepository.save(new Driver(request.driver().driverCode(), request.driver().name())));

        // 2. Find or create Vehicle
        Vehicle vehicle = vehicleRepository.findByRegistrationNumber(request.vehicle().registrationNumber())
                .orElseGet(() -> vehicleRepository.save(new Vehicle(request.vehicle().registrationNumber())));

        // 3. Find or create Employees
        Map<String, Employee> employeeMap = new HashMap<>();
        for (EmployeeRequest empReq : request.employees()) {
            Employee emp = employeeRepository.findByEmployeeCode(empReq.employeeCode())
                    .orElseGet(() -> employeeRepository.save(new Employee(empReq.employeeCode(), empReq.gender())));
            employeeMap.put(empReq.employeeCode(), emp);
        }

        // 4. Create Trip
        Trip trip = new Trip(driver.getId(), vehicle.getId(), Boolean.TRUE.equals(request.escortAssigned()), Instant.now());
        trip = tripRepository.save(trip);
        Long tripId = trip.getId();

        // 5. Create RouteSegments
        RouteRequest route = request.route();
        List<List<Double>> points = route.points();
        List<Double> speedLimits = route.speedLimitsKmph();
        for (int i = 0; i < points.size() - 1; i++) {
            List<Double> p1 = points.get(i);
            List<Double> p2 = points.get(i + 1);
            RouteSegment segment = new RouteSegment(
                    tripId,
                    i,
                    new GeoPoint(p1.get(0), p1.get(1)),
                    new GeoPoint(p2.get(0), p2.get(1)),
                    speedLimits.get(i)
            );
            routeSegmentRepository.save(segment);
        }

        // 6. Create Stops
        List<StopRequest> sortedStops = request.stops().stream()
                .sorted(Comparator.comparingInt(StopRequest::sequence))
                .toList();

        List<StopResponse> stopResponses = new ArrayList<>();
        for (StopRequest stopReq : sortedStops) {
            Employee emp = employeeMap.get(stopReq.employeeCode());
            String expectedOtp = stopReq.type() == StopType.DROP ? stopReq.otp() : null;

            Stop stop = new Stop(
                    tripId,
                    stopReq.sequence(),
                    stopReq.type(),
                    emp.getId(),
                    stopReq.plannedTime(),
                    expectedOtp
            );
            stop = stopRepository.save(stop);

            // Response NEVER includes expectedOtp or any OTP value
            stopResponses.add(new StopResponse(
                    stop.getId(),
                    stop.getSequence(),
                    stop.getType(),
                    stop.getEmployeeId(),
                    stopReq.employeeCode(),
                    stop.getPlannedTime(),
                    stop.getStatus()
            ));
        }

        return new TripResponse(
                trip.getId(),
                trip.getDriverId(),
                trip.getVehicleId(),
                trip.isEscortAssigned(),
                trip.getStatus(),
                trip.getCreatedAt(),
                trip.getCompletedAt(),
                stopResponses
        );
    }

    private void validateTripRequest(CreateTripRequest request) {
        // Validate route points and speed limits
        RouteRequest route = request.route();
        if (route.points() == null || route.points().size() < 2) {
            throw new IllegalArgumentException("Route must contain at least 2 points");
        }
        for (List<Double> pt : route.points()) {
            if (pt == null || pt.size() != 2) {
                throw new IllegalArgumentException("Each route point must be a [latitude, longitude] pair");
            }
            double lat = pt.get(0);
            double lon = pt.get(1);
            if (lat < -90.0 || lat > 90.0) {
                throw new IllegalArgumentException("Latitude must be between -90 and 90");
            }
            if (lon < -180.0 || lon > 180.0) {
                throw new IllegalArgumentException("Longitude must be between -180 and 180");
            }
        }
        if (route.speedLimitsKmph() == null || route.speedLimitsKmph().size() != route.points().size() - 1) {
            throw new IllegalArgumentException("speedLimitsKmph length must equal points length - 1");
        }
        for (Double speed : route.speedLimitsKmph()) {
            if (speed == null || speed < 0.0) {
                throw new IllegalArgumentException("Speed limit must be non-negative");
            }
        }

        // Validate unique stop sequences
        Set<Integer> sequences = new HashSet<>();
        for (StopRequest stop : request.stops()) {
            if (!sequences.add(stop.sequence())) {
                throw new IllegalArgumentException("Stop sequence numbers must be unique: " + stop.sequence());
            }
        }

        // Validate employees and stops
        Set<String> employeeCodes = request.employees().stream()
                .map(EmployeeRequest::employeeCode)
                .collect(Collectors.toSet());

        for (String code : employeeCodes) {
            List<StopRequest> employeeStops = request.stops().stream()
                    .filter(s -> code.equals(s.employeeCode()))
                    .toList();

            List<StopRequest> pickups = employeeStops.stream().filter(s -> s.type() == StopType.PICKUP).toList();
            List<StopRequest> drops = employeeStops.stream().filter(s -> s.type() == StopType.DROP).toList();

            if (pickups.size() != 1 || drops.size() != 1) {
                throw new IllegalArgumentException("Employee " + code + " must have exactly one PICKUP and one DROP stop");
            }

            if (pickups.get(0).sequence() >= drops.get(0).sequence()) {
                throw new IllegalArgumentException("Employee " + code + " PICKUP must occur before DROP in sequence");
            }
        }

        // Ensure every DROP has an OTP
        for (StopRequest stop : request.stops()) {
            if (stop.type() == StopType.DROP && (stop.otp() == null || stop.otp().isBlank())) {
                throw new IllegalArgumentException("Every DROP stop must have an expected OTP");
            }
        }
    }

    /**
     * Ingests a batch of GPS fixes, ignoring duplicate timestamps for the trip.
     * Rejects ingestion if trip is already COMPLETED (409 Conflict).
     */
    @Transactional
    public GpsBatchResponse ingestGps(Long tripId, GpsBatchRequest request) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new NotFoundException("Trip not found: " + tripId));

        if (trip.getStatus() == TripStatus.COMPLETED) {
            throw new ConflictException("Cannot accept GPS fixes after trip is COMPLETED: " + tripId);
        }

        Set<Instant> existingTimestamps = gpsFixRepository.findByTripIdOrderByRecordedAtAsc(tripId).stream()
                .map(GpsFix::getRecordedAt)
                .collect(Collectors.toCollection(HashSet::new));

        int accepted = 0;
        int duplicatesIgnored = 0;
        List<GpsFix> fixesToSave = new ArrayList<>();

        for (GpsFixRequest fix : request.fixes()) {
            if (existingTimestamps.contains(fix.recordedAt())) {
                duplicatesIgnored++;
            } else {
                existingTimestamps.add(fix.recordedAt());
                fixesToSave.add(new GpsFix(tripId, fix.recordedAt(), fix.lat(), fix.lon(), fix.speedKmph()));
                accepted++;
            }
        }

        gpsFixRepository.saveAll(fixesToSave);
        return new GpsBatchResponse(accepted, duplicatesIgnored);
    }

    /**
     * Ingests an event (OTP_ENTERED, ESCORT_ASSIGNED, ESCORT_REMOVED, TRIP_COMPLETED).
     * Rejects any event if trip is already COMPLETED (409 Conflict).
     */
    @Transactional
    public TripEventResponse recordEvent(Long tripId, TripEventRequest request) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new NotFoundException("Trip not found: " + tripId));

        if (trip.getStatus() == TripStatus.COMPLETED) {
            throw new ConflictException("Cannot accept events after trip is COMPLETED: " + tripId);
        }

        TripEventType type = request.type();
        Instant occurredAt = request.occurredAt();

        if (type == TripEventType.OTP_ENTERED) {
            if (request.stopId() == null) {
                throw new IllegalArgumentException("stopId is required for OTP_ENTERED event");
            }
            Stop stop = stopRepository.findById(request.stopId())
                    .orElseThrow(() -> new NotFoundException("Stop not found: " + request.stopId()));

            // Stop must belong to the trip, else 404
            if (!tripId.equals(stop.getTripId())) {
                throw new NotFoundException("Stop " + request.stopId() + " does not belong to trip " + tripId);
            }

            boolean matched = otpVerifier.verify(request.otp(), stop.getExpectedOtp());

            if (!matched) {
                // Wrong OTP freezes the stop
                stop.setStatus(StopStatus.FROZEN);
                stopRepository.save(stop);
            } else {
                // On match, complete the stop unless already frozen
                if (stop.getStatus() != StopStatus.FROZEN) {
                    stop.setStatus(StopStatus.COMPLETED);
                    stopRepository.save(stop);
                }
            }

            // The entered OTP is deliberately NEVER stored, logged or returned
            TripEvent event = new TripEvent(tripId, TripEventType.OTP_ENTERED, occurredAt, stop.getId(), matched, null, Instant.now());
            event = tripEventRepository.save(event);

            return new TripEventResponse(event.getId(), stop.getStatus());
        } else if (type == TripEventType.TRIP_COMPLETED) {
            trip.complete(occurredAt);
            tripRepository.save(trip);

            TripEvent event = new TripEvent(tripId, TripEventType.TRIP_COMPLETED, occurredAt, null, null, null, Instant.now());
            event = tripEventRepository.save(event);

            return new TripEventResponse(event.getId(), null);
        } else if (type == TripEventType.ESCORT_ASSIGNED || type == TripEventType.ESCORT_REMOVED) {
            TripEvent event = new TripEvent(tripId, type, occurredAt, null, null, null, Instant.now());
            event = tripEventRepository.save(event);

            return new TripEventResponse(event.getId(), null);
        } else {
            throw new IllegalArgumentException("Unsupported event type for /events endpoint: " + type);
        }
    }

    /**
     * Lists violations for a trip.
     */
    public List<Violation> getTripViolations(Long tripId) {
        if (!tripRepository.existsById(tripId)) {
            throw new NotFoundException("Trip not found: " + tripId);
        }
        return violationRepository.findByTripIdOrderByDetectedAtAsc(tripId);
    }

    public record ViolationDetail(Violation violation, List<AlertDelivery> deliveries) {}

    /**
     * Gets a single violation with its alert deliveries.
     */
    public ViolationDetail getViolation(Long violationId) {
        Violation violation = violationRepository.findById(violationId)
                .orElseThrow(() -> new NotFoundException("Violation not found: " + violationId));
        List<AlertDelivery> deliveries = alertDeliveryRepository.findByViolationId(violationId);
        return new ViolationDetail(violation, deliveries);
    }
}

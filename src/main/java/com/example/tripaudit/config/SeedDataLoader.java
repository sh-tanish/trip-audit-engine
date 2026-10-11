package com.example.tripaudit.config;

import com.example.tripaudit.domain.*;
import com.example.tripaudit.repository.*;
import java.io.InputStream;
import java.time.Instant;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Loads baseline demo trips from src/main/resources/seed at startup when running in the dev profile.
 * Only runs if the database has 0 trips, ensuring restarts do not duplicate or fail on unique keys.
 */
@Component
@Profile("dev")
public class SeedDataLoader implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SeedDataLoader.class);

    private static final List<String> SEED_FILES = List.of(
            "seed/trip-a-night-safety.json",
            "seed/trip-b-route-deviation.json",
            "seed/trip-c-otp-mismatch.json",
            "seed/trip-d-overspeed.json",
            "seed/trip-e-clean.json"
    );

    private final ObjectMapper objectMapper;
    private final DriverRepository driverRepository;
    private final VehicleRepository vehicleRepository;
    private final EmployeeRepository employeeRepository;
    private final TripRepository tripRepository;
    private final StopRepository stopRepository;
    private final RouteSegmentRepository routeSegmentRepository;
    private final GpsFixRepository gpsFixRepository;
    private final TripEventRepository tripEventRepository;

    public SeedDataLoader(ObjectMapper objectMapper,
                          DriverRepository driverRepository,
                          VehicleRepository vehicleRepository,
                          EmployeeRepository employeeRepository,
                          TripRepository tripRepository,
                          StopRepository stopRepository,
                          RouteSegmentRepository routeSegmentRepository,
                          GpsFixRepository gpsFixRepository,
                          TripEventRepository tripEventRepository) {
        this.objectMapper = objectMapper;
        this.driverRepository = driverRepository;
        this.vehicleRepository = vehicleRepository;
        this.employeeRepository = employeeRepository;
        this.tripRepository = tripRepository;
        this.stopRepository = stopRepository;
        this.routeSegmentRepository = routeSegmentRepository;
        this.gpsFixRepository = gpsFixRepository;
        this.tripEventRepository = tripEventRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (tripRepository.count() > 0) {
            log.info("Database already contains trips. Skipping seed data ingestion.");
            return;
        }

        log.info("Seeding initial trips from JSON...");
        for (String file : SEED_FILES) {
            loadSeedFile(file);
        }
        log.info("Successfully seeded {} trips.", SEED_FILES.size());
    }

    private void loadSeedFile(String resourcePath) {
        try {
            ClassPathResource resource = new ClassPathResource(resourcePath);
            try (InputStream is = resource.getInputStream()) {
                JsonNode root = objectMapper.readTree(is);

                // 1. Driver
                JsonNode driverNode = root.get("driver");
                String driverCode = driverNode.get("driverCode").asText();
                String driverName = driverNode.get("name").asText();
                Driver driver = driverRepository.findByDriverCode(driverCode)
                        .orElseGet(() -> driverRepository.save(new Driver(driverCode, driverName)));

                // 2. Vehicle
                JsonNode vehicleNode = root.get("vehicle");
                String regNumber = vehicleNode.get("registrationNumber").asText();
                Vehicle vehicle = vehicleRepository.findByRegistrationNumber(regNumber)
                        .orElseGet(() -> vehicleRepository.save(new Vehicle(regNumber)));

                // 3. Employees
                Map<String, Employee> employeeMap = new HashMap<>();
                for (JsonNode empNode : root.get("employees")) {
                    String code = empNode.get("employeeCode").asText();
                    Gender gender = Gender.valueOf(empNode.get("gender").asText());
                    Employee employee = employeeRepository.findByEmployeeCode(code)
                            .orElseGet(() -> employeeRepository.save(new Employee(code, gender)));
                    employeeMap.put(code, employee);
                }

                // 4. Trip
                boolean escortAssigned = root.get("escortAssigned").asBoolean();
                Instant createdAt = Instant.parse(root.get("createdAt").asText());
                Instant completedAt = Instant.parse(root.get("completedAt").asText());
                Trip trip = new Trip(driver.getId(), vehicle.getId(), escortAssigned, createdAt);
                trip.complete(completedAt);
                trip = tripRepository.save(trip);
                Long tripId = trip.getId();

                // 5. Stops
                Map<Integer, Stop> stopsBySeq = new HashMap<>();
                for (JsonNode stopNode : root.get("stops")) {
                    int seq = stopNode.get("sequence").asInt();
                    StopType type = StopType.valueOf(stopNode.get("type").asText());
                    String empCode = stopNode.get("employeeCode").asText();
                    Long empId = employeeMap.get(empCode).getId();
                    Instant plannedTime = Instant.parse(stopNode.get("plannedTime").asText());
                    String expectedOtp = stopNode.hasNonNull("expectedOtp") ? stopNode.get("expectedOtp").asText() : null;

                    Stop stop = new Stop(tripId, seq, type, empId, plannedTime, expectedOtp);
                    // If this stop had an OTP mismatch, mark it FROZEN per case study rules
                    stop = stopRepository.save(stop);
                    stopsBySeq.put(seq, stop);
                }

                // 6. Route Segments
                for (JsonNode segNode : root.get("routeSegments")) {
                    int seq = segNode.get("seq").asInt();
                    double startLat = segNode.get("startLat").asDouble();
                    double startLon = segNode.get("startLon").asDouble();
                    double endLat = segNode.get("endLat").asDouble();
                    double endLon = segNode.get("endLon").asDouble();
                    double speedLimit = segNode.get("speedLimitKmph").asDouble();

                    RouteSegment seg = new RouteSegment(tripId, seq,
                            new GeoPoint(startLat, startLon),
                            new GeoPoint(endLat, endLon),
                            speedLimit);
                    routeSegmentRepository.save(seg);
                }

                // 7. GPS Fixes
                List<GpsFix> fixes = new ArrayList<>();
                for (JsonNode fixNode : root.get("gpsFixes")) {
                    Instant recordedAt = Instant.parse(fixNode.get("recordedAt").asText());
                    double lat = fixNode.get("lat").asDouble();
                    double lon = fixNode.get("lon").asDouble();
                    double speed = fixNode.get("speedKmph").asDouble();
                    fixes.add(new GpsFix(tripId, recordedAt, lat, lon, speed));
                }
                gpsFixRepository.saveAll(fixes);

                // 8. Trip Events
                for (JsonNode eventNode : root.get("events")) {
                    TripEventType type = TripEventType.valueOf(eventNode.get("type").asText());
                    Instant occurredAt = Instant.parse(eventNode.get("occurredAt").asText());
                    Long stopId = null;
                    Boolean otpMatched = null;
                    if (eventNode.hasNonNull("stopSequence")) {
                        int stopSeq = eventNode.get("stopSequence").asInt();
                        Stop stop = stopsBySeq.get(stopSeq);
                        if (stop != null) {
                            stopId = stop.getId();
                        }
                    }
                    if (eventNode.hasNonNull("otpMatched")) {
                        otpMatched = eventNode.get("otpMatched").asBoolean();
                        // If OTP mismatched, freeze the stop
                        if (Boolean.FALSE.equals(otpMatched) && stopId != null) {
                            Stop stop = stopRepository.findById(stopId).orElse(null);
                            if (stop != null) {
                                stop.setStatus(StopStatus.FROZEN);
                                stopRepository.save(stop);
                            }
                        } else if (Boolean.TRUE.equals(otpMatched) && stopId != null) {
                            Stop stop = stopRepository.findById(stopId).orElse(null);
                            if (stop != null) {
                                stop.setStatus(StopStatus.COMPLETED);
                                stopRepository.save(stop);
                            }
                        }
                    }
                    tripEventRepository.save(new TripEvent(tripId, type, occurredAt, stopId, otpMatched, null, occurredAt));
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load seed file: " + resourcePath, e);
        }
    }
}

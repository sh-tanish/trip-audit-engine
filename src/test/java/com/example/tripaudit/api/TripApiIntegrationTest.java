package com.example.tripaudit.api;

import com.example.tripaudit.api.dto.CreateTripRequest;
import com.example.tripaudit.api.dto.DriverRequest;
import com.example.tripaudit.api.dto.EmployeeRequest;
import com.example.tripaudit.api.dto.GpsBatchRequest;
import com.example.tripaudit.api.dto.GpsFixRequest;
import com.example.tripaudit.api.dto.RouteRequest;
import com.example.tripaudit.api.dto.SosRequest;
import com.example.tripaudit.api.dto.StopRequest;
import com.example.tripaudit.api.dto.TripEventRequest;
import com.example.tripaudit.api.dto.VehicleRequest;
import com.example.tripaudit.domain.Gender;
import com.example.tripaudit.domain.StopType;
import com.example.tripaudit.domain.TripEventType;
import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class TripApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String randomSuffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private CreateTripRequest createValidTripRequest(String suffix) {
        String emp1 = "EMP1-" + suffix;
        String emp2 = "EMP2-" + suffix;
        return new CreateTripRequest(
                new DriverRequest("DRV-" + suffix, "Driver " + suffix),
                new VehicleRequest("KA01-" + suffix),
                false,
                List.of(
                        new EmployeeRequest(emp1, Gender.FEMALE),
                        new EmployeeRequest(emp2, Gender.MALE)
                ),
                List.of(
                        new StopRequest(1, StopType.PICKUP, emp1, Instant.parse("2026-10-11T22:00:00Z"), null),
                        new StopRequest(2, StopType.PICKUP, emp2, Instant.parse("2026-10-11T22:15:00Z"), null),
                        new StopRequest(3, StopType.DROP, emp2, Instant.parse("2026-10-11T22:45:00Z"), "1234"),
                        new StopRequest(4, StopType.DROP, emp1, Instant.parse("2026-10-11T23:10:00Z"), "5678")
                ),
                new RouteRequest(
                        List.of(
                                List.of(12.9716, 77.5946),
                                List.of(12.9352, 77.6245),
                                List.of(12.9081, 77.6476)
                        ),
                        List.of(50.0, 40.0)
                )
        );
    }

    private Long createTripAndGetId(String suffix) throws Exception {
        CreateTripRequest req = createValidTripRequest(suffix);
        MvcResult res = mockMvc.perform(post("/api/v1/trips")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();

        return ((Number) JsonPath.read(res.getResponse().getContentAsString(), "$.id")).longValue();
    }

    @Test
    @DisplayName("POST /trips creates trip, returns stop IDs, and never exposes OTP")
    void createTrip_success() throws Exception {
        String suffix = randomSuffix();
        CreateTripRequest req = createValidTripRequest(suffix);

        MvcResult result = mockMvc.perform(post("/api/v1/trips")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.status", is("ACTIVE")))
                .andExpect(jsonPath("$.stops", hasSize(4)))
                .andExpect(jsonPath("$.stops[0].id", notNullValue()))
                .andExpect(jsonPath("$.stops[0].sequence", is(1)))
                .andExpect(jsonPath("$.stops[2].id", notNullValue()))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertFalse(body.contains("1234"), "Response must not contain expected OTP 1234");
        assertFalse(body.contains("5678"), "Response must not contain expected OTP 5678");
        assertFalse(body.contains("\"otp\""), "Response must not contain 'otp' field");
        assertFalse(body.contains("\"expectedOtp\""), "Response must not contain 'expectedOtp' field");
    }

    @Test
    @DisplayName("POST /trips validation fails when route points count < 2")
    void createTrip_routePointsLessThanTwo() throws Exception {
        String suffix = randomSuffix();
        CreateTripRequest req = new CreateTripRequest(
                new DriverRequest("DRV-" + suffix, "Driver " + suffix),
                new VehicleRequest("KA01-" + suffix),
                false,
                List.of(new EmployeeRequest("EMP-" + suffix, Gender.MALE)),
                List.of(
                        new StopRequest(1, StopType.PICKUP, "EMP-" + suffix, Instant.parse("2026-10-11T22:00:00Z"), null),
                        new StopRequest(2, StopType.DROP, "EMP-" + suffix, Instant.parse("2026-10-11T22:45:00Z"), "1234")
                ),
                new RouteRequest(
                        List.of(List.of(12.9716, 77.5946)),
                        List.of()
                )
        );

        mockMvc.perform(post("/api/v1/trips")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title", is("Bad Request")));
    }

    @Test
    @DisplayName("POST /trips validation fails when speed limits length != points length - 1")
    void createTrip_speedLimitsLengthMismatch() throws Exception {
        String suffix = randomSuffix();
        CreateTripRequest req = new CreateTripRequest(
                new DriverRequest("DRV-" + suffix, "Driver " + suffix),
                new VehicleRequest("KA01-" + suffix),
                false,
                List.of(new EmployeeRequest("EMP-" + suffix, Gender.MALE)),
                List.of(
                        new StopRequest(1, StopType.PICKUP, "EMP-" + suffix, Instant.parse("2026-10-11T22:00:00Z"), null),
                        new StopRequest(2, StopType.DROP, "EMP-" + suffix, Instant.parse("2026-10-11T22:45:00Z"), "1234")
                ),
                new RouteRequest(
                        List.of(List.of(12.9716, 77.5946), List.of(12.9352, 77.6245), List.of(12.9081, 77.6476)),
                        List.of(50.0) // needs 2 speed limits
                )
        );

        mockMvc.perform(post("/api/v1/trips")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /trips validation fails when employee PICKUP is not before DROP")
    void createTrip_pickupAfterDrop() throws Exception {
        String suffix = randomSuffix();
        String emp = "EMP-" + suffix;
        CreateTripRequest req = new CreateTripRequest(
                new DriverRequest("DRV-" + suffix, "Driver " + suffix),
                new VehicleRequest("KA01-" + suffix),
                false,
                List.of(new EmployeeRequest(emp, Gender.MALE)),
                List.of(
                        new StopRequest(1, StopType.DROP, emp, Instant.parse("2026-10-11T22:00:00Z"), "1234"),
                        new StopRequest(2, StopType.PICKUP, emp, Instant.parse("2026-10-11T22:45:00Z"), null)
                ),
                new RouteRequest(
                        List.of(List.of(12.9716, 77.5946), List.of(12.9352, 77.6245)),
                        List.of(50.0)
                )
        );

        mockMvc.perform(post("/api/v1/trips")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /trips validation fails when DROP stop is missing OTP")
    void createTrip_dropWithoutOtp() throws Exception {
        String suffix = randomSuffix();
        String emp = "EMP-" + suffix;
        CreateTripRequest req = new CreateTripRequest(
                new DriverRequest("DRV-" + suffix, "Driver " + suffix),
                new VehicleRequest("KA01-" + suffix),
                false,
                List.of(new EmployeeRequest(emp, Gender.MALE)),
                List.of(
                        new StopRequest(1, StopType.PICKUP, emp, Instant.parse("2026-10-11T22:00:00Z"), null),
                        new StopRequest(2, StopType.DROP, emp, Instant.parse("2026-10-11T22:45:00Z"), "")
                ),
                new RouteRequest(
                        List.of(List.of(12.9716, 77.5946), List.of(12.9352, 77.6245)),
                        List.of(50.0)
                )
        );

        mockMvc.perform(post("/api/v1/trips")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Bean validation errors return field names only and never rejected values")
    void validationError_fieldNamesOnly() throws Exception {
        String invalidPayload = """
                {
                  "driver": { "driverCode": "", "name": "Test" },
                  "vehicle": { "registrationNumber": "SECRET_REG_9999" },
                  "escortAssigned": false,
                  "employees": [],
                  "stops": [],
                  "route": { "points": [], "speedLimitsKmph": [] }
                }
                """;

        MvcResult result = mockMvc.perform(post("/api/v1/trips")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidPayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title", is("Bad Request")))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        // Ensure field name is present
        assertTrue(body.contains("driver.driverCode") || body.contains("invalidFields"), "Must list invalid field name");
        // Ensure sensitive rejected value is NOT present
        assertFalse(body.contains("SECRET_REG_9999"), "Must not leak rejected values");
    }

    @Test
    @DisplayName("Malformed JSON returns 400 Bad Request with no stack trace or class names")
    void malformedJsonReturns400() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/trips")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{invalid-json-body"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title", is("Bad Request")))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertFalse(body.contains("Exception"), "Must not leak exception class names");
        assertFalse(body.contains("at com.example"), "Must not leak stack traces");
    }

    @Test
    @DisplayName("POST /trips/{id}/gps ingests fixes and skips duplicate timestamps")
    void ingestGps_batchAndDuplicates() throws Exception {
        Long tripId = createTripAndGetId(randomSuffix());

        GpsBatchRequest batch1 = new GpsBatchRequest(List.of(
                new GpsFixRequest(Instant.parse("2026-10-11T22:10:00Z"), 12.95, 77.61, 40.0),
                new GpsFixRequest(Instant.parse("2026-10-11T22:10:30Z"), 12.952, 77.612, 42.0)
        ));

        mockMvc.perform(post("/api/v1/trips/" + tripId + "/gps")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(batch1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accepted", is(2)))
                .andExpect(jsonPath("$.duplicatesIgnored", is(0)));

        // Batch 2 includes one existing timestamp, one new, and one duplicate within the batch
        GpsBatchRequest batch2 = new GpsBatchRequest(List.of(
                new GpsFixRequest(Instant.parse("2026-10-11T22:10:30Z"), 12.952, 77.612, 42.0),
                new GpsFixRequest(Instant.parse("2026-10-11T22:11:00Z"), 12.954, 77.614, 45.0),
                new GpsFixRequest(Instant.parse("2026-10-11T22:11:00Z"), 12.954, 77.614, 45.0)
        ));

        mockMvc.perform(post("/api/v1/trips/" + tripId + "/gps")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(batch2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accepted", is(1)))
                .andExpect(jsonPath("$.duplicatesIgnored", is(2)));
    }

    @Test
    @DisplayName("POST /trips/{id}/gps on unknown trip returns 404")
    void ingestGps_unknownTrip() throws Exception {
        GpsBatchRequest batch = new GpsBatchRequest(List.of(
                new GpsFixRequest(Instant.parse("2026-10-11T22:10:00Z"), 12.95, 77.61, 40.0)
        ));

        mockMvc.perform(post("/api/v1/trips/999999/gps")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(batch)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title", is("Not Found")));
    }

    @Test
    @DisplayName("POST /trips/{id}/events handles OTP matching, mismatch freeze, and persistence")
    void events_otpMatchingAndFreeze() throws Exception {
        String suffix = randomSuffix();
        CreateTripRequest req = createValidTripRequest(suffix);

        MvcResult tripResult = mockMvc.perform(post("/api/v1/trips")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();

        Long tripId = ((Number) JsonPath.read(tripResult.getResponse().getContentAsString(), "$.id")).longValue();
        // Stop 3 is DROP for emp2 with expected OTP "1234"
        Long stop3Id = ((Number) JsonPath.read(tripResult.getResponse().getContentAsString(), "$.stops[2].id")).longValue();
        // Stop 4 is DROP for emp1 with expected OTP "5678"
        Long stop4Id = ((Number) JsonPath.read(tripResult.getResponse().getContentAsString(), "$.stops[3].id")).longValue();

        // 1. Correct OTP on stop 3 -> COMPLETED
        TripEventRequest correctOtp = new TripEventRequest(
                TripEventType.OTP_ENTERED,
                stop3Id,
                "1234",
                Instant.parse("2026-10-11T22:45:10Z")
        );
        mockMvc.perform(post("/api/v1/trips/" + tripId + "/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(correctOtp)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.eventId", notNullValue()))
                .andExpect(jsonPath("$.stopStatus", is("COMPLETED")));

        // 2. Wrong OTP on stop 4 -> FROZEN
        TripEventRequest wrongOtp = new TripEventRequest(
                TripEventType.OTP_ENTERED,
                stop4Id,
                "9999",
                Instant.parse("2026-10-11T23:10:05Z")
        );
        mockMvc.perform(post("/api/v1/trips/" + tripId + "/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(wrongOtp)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.eventId", notNullValue()))
                .andExpect(jsonPath("$.stopStatus", is("FROZEN")));

        // 3. Right OTP on now-FROZEN stop 4 stays FROZEN
        TripEventRequest retryRightOtp = new TripEventRequest(
                TripEventType.OTP_ENTERED,
                stop4Id,
                "5678",
                Instant.parse("2026-10-11T23:11:00Z")
        );
        mockMvc.perform(post("/api/v1/trips/" + tripId + "/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(retryRightOtp)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.stopStatus", is("FROZEN")));
    }

    @Test
    @DisplayName("POST /trips/{id}/events returns 404 if stop does not belong to the trip")
    void events_stopDoesNotBelongToTrip() throws Exception {
        Long trip1Id = createTripAndGetId(randomSuffix());

        MvcResult trip2Result = mockMvc.perform(post("/api/v1/trips")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createValidTripRequest(randomSuffix()))))
                .andExpect(status().isCreated())
                .andReturn();
        Long trip2StopId = ((Number) JsonPath.read(trip2Result.getResponse().getContentAsString(), "$.stops[2].id")).longValue();

        TripEventRequest event = new TripEventRequest(
                TripEventType.OTP_ENTERED,
                trip2StopId,
                "1234",
                Instant.parse("2026-10-11T22:45:00Z")
        );

        mockMvc.perform(post("/api/v1/trips/" + trip1Id + "/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(event)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title", is("Not Found")));
    }

    @Test
    @DisplayName("POST /trips/{id}/events handles ESCORT and completes trip, then returns 409 for later events and GPS")
    void events_lifecycleAndConflictAfterCompletion() throws Exception {
        Long tripId = createTripAndGetId(randomSuffix());

        // Escort assigned
        TripEventRequest escortAssigned = new TripEventRequest(
                TripEventType.ESCORT_ASSIGNED,
                null,
                null,
                Instant.parse("2026-10-11T22:20:00Z")
        );
        mockMvc.perform(post("/api/v1/trips/" + tripId + "/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(escortAssigned)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.eventId", notNullValue()));

        // Trip completed
        TripEventRequest tripCompleted = new TripEventRequest(
                TripEventType.TRIP_COMPLETED,
                null,
                null,
                Instant.parse("2026-10-11T23:30:00Z")
        );
        mockMvc.perform(post("/api/v1/trips/" + tripId + "/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(tripCompleted)))
                .andExpect(status().isCreated());

        // GPS after completion -> 409 Conflict
        GpsBatchRequest gps = new GpsBatchRequest(List.of(
                new GpsFixRequest(Instant.parse("2026-10-11T23:31:00Z"), 12.95, 77.61, 40.0)
        ));
        mockMvc.perform(post("/api/v1/trips/" + tripId + "/gps")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(gps)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title", is("Conflict")));

        // Event after completion -> 409 Conflict
        TripEventRequest lateEvent = new TripEventRequest(
                TripEventType.ESCORT_REMOVED,
                null,
                null,
                Instant.parse("2026-10-11T23:32:00Z")
        );
        mockMvc.perform(post("/api/v1/trips/" + tripId + "/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(lateEvent)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title", is("Conflict")));
    }

    @Test
    @DisplayName("POST /trips/{id}/sos returns 201 for new, 200 for exact retry, and 403 for unauthorized employee")
    void sos_endpoint() throws Exception {
        String suffix = randomSuffix();
        MvcResult tripResult = mockMvc.perform(post("/api/v1/trips")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createValidTripRequest(suffix))))
                .andExpect(status().isCreated())
                .andReturn();

        Long tripId = ((Number) JsonPath.read(tripResult.getResponse().getContentAsString(), "$.id")).longValue();
        Long validEmpId = ((Number) JsonPath.read(tripResult.getResponse().getContentAsString(), "$.stops[0].employeeId")).longValue();

        Instant pressTime = Instant.parse("2026-10-11T22:25:00Z");
        SosRequest sosReq = new SosRequest(validEmpId, pressTime);

        // 1. First press -> 201 Created
        mockMvc.perform(post("/api/v1/trips/" + tripId + "/sos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sosReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type", is("SOS")))
                .andExpect(jsonPath("$.severity", is("CRITICAL")))
                .andExpect(jsonPath("$.employeeId", is(validEmpId.intValue())));

        // 2. Exact retry -> 200 OK
        mockMvc.perform(post("/api/v1/trips/" + tripId + "/sos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sosReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type", is("SOS")));

        // 3. Forbidden employee not on trip -> 403 Forbidden
        SosRequest forbiddenReq = new SosRequest(999999L, pressTime);
        mockMvc.perform(post("/api/v1/trips/" + tripId + "/sos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(forbiddenReq)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title", is("Forbidden")));
    }

    @Test
    @DisplayName("POST /trips/{id}/audit: 409 before completion, 200 after completion, 404 for unknown trip")
    void audit_endpoint() throws Exception {
        Long tripId = createTripAndGetId(randomSuffix());

        // 1. Audit before completion -> 409 Conflict
        mockMvc.perform(post("/api/v1/trips/" + tripId + "/audit"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title", is("Conflict")));

        // 2. Complete trip
        TripEventRequest completeEvent = new TripEventRequest(
                TripEventType.TRIP_COMPLETED,
                null,
                null,
                Instant.parse("2026-10-11T23:30:00Z")
        );
        mockMvc.perform(post("/api/v1/trips/" + tripId + "/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(completeEvent)))
                .andExpect(status().isCreated());

        // 3. Audit completed trip -> 200 OK
        mockMvc.perform(post("/api/v1/trips/" + tripId + "/audit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.tripId", is(tripId.intValue())));

        // 4. Audit unknown trip -> 404 Not Found
        mockMvc.perform(post("/api/v1/trips/999999/audit"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title", is("Not Found")));
    }

    @Test
    @DisplayName("GET /trips/{id}/violations and GET /violations/{id} return expected details")
    void violations_queries() throws Exception {
        String suffix = randomSuffix();
        MvcResult tripResult = mockMvc.perform(post("/api/v1/trips")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createValidTripRequest(suffix))))
                .andExpect(status().isCreated())
                .andReturn();

        Long tripId = ((Number) JsonPath.read(tripResult.getResponse().getContentAsString(), "$.id")).longValue();
        Long empId = ((Number) JsonPath.read(tripResult.getResponse().getContentAsString(), "$.stops[0].employeeId")).longValue();

        // Raise an SOS to create a violation
        SosRequest sosReq = new SosRequest(empId, Instant.parse("2026-10-11T22:30:00Z"));
        MvcResult sosResult = mockMvc.perform(post("/api/v1/trips/" + tripId + "/sos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sosReq)))
                .andExpect(status().isCreated())
                .andReturn();

        Long violationId = ((Number) JsonPath.read(sosResult.getResponse().getContentAsString(), "$.id")).longValue();

        // Query violations for trip
        mockMvc.perform(get("/api/v1/trips/" + tripId + "/violations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$[0].id", is(violationId.intValue())));

        // Query violation by id
        mockMvc.perform(get("/api/v1/violations/" + violationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(violationId.intValue())))
                .andExpect(jsonPath("$.type", is("SOS")))
                .andExpect(jsonPath("$.deliveries", notNullValue()));

        // Unknown violation id -> 404
        mockMvc.perform(get("/api/v1/violations/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title", is("Not Found")));
    }

    @Test
    @DisplayName("Timestamps must serialize as ISO-8601 strings and NOT numbers")
    void timestamps_serializeAsIso8601Strings() throws Exception {
        MvcResult tripResult = mockMvc.perform(post("/api/v1/trips")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createValidTripRequest(randomSuffix()))))
                .andExpect(status().isCreated())
                .andReturn();

        String content = tripResult.getResponse().getContentAsString();
        Object createdAt = JsonPath.read(content, "$.createdAt");
        Object plannedTime = JsonPath.read(content, "$.stops[0].plannedTime");

        assertInstanceOf(String.class, createdAt, "createdAt must be a String");
        assertInstanceOf(String.class, plannedTime, "plannedTime must be a String");

        String createdAtStr = (String) createdAt;
        String plannedTimeStr = (String) plannedTime;

        // Ensure string matches ISO-8601 regex pattern
        assertTrue(createdAtStr.matches("^\\d{4}-\\d{2}-\\d{2}T.*"), "createdAt must match ISO-8601 format: " + createdAtStr);
        assertTrue(plannedTimeStr.matches("^\\d{4}-\\d{2}-\\d{2}T.*"), "plannedTime must match ISO-8601 format: " + plannedTimeStr);
    }
}

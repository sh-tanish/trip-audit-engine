package com.example.tripaudit.rules;

import com.example.tripaudit.domain.Employee;
import com.example.tripaudit.domain.Gender;
import com.example.tripaudit.domain.GeoPoint;
import com.example.tripaudit.domain.RouteSegment;
import com.example.tripaudit.domain.Stop;
import com.example.tripaudit.domain.StopType;
import com.example.tripaudit.domain.TripEvent;
import com.example.tripaudit.domain.TripEventType;
import java.time.Instant;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Shared factory helpers for instantiating domain entities with assigned IDs in unit tests.
 */
public final class TestFixtures {

    private TestFixtures() {}

    public static Employee employee(Long id, String code, Gender gender) {
        Employee e = new Employee(code, gender);
        ReflectionTestUtils.setField(e, "id", id);
        return e;
    }

    public static Stop stop(Long id, Long tripId, int seq, StopType type, Long empId, Instant time, String expectedOtp) {
        Stop s = new Stop(tripId, seq, type, empId, time, expectedOtp);
        ReflectionTestUtils.setField(s, "id", id);
        return s;
    }

    public static RouteSegment segment(Long id, Long tripId, int seq, GeoPoint start, GeoPoint end, double limitKmph) {
        RouteSegment seg = new RouteSegment(tripId, seq, start, end, limitKmph);
        ReflectionTestUtils.setField(seg, "id", id);
        return seg;
    }

    public static TripEvent otpEvent(Long id, Long tripId, Long stopId, boolean matched, Instant time) {
        TripEvent e = new TripEvent(tripId, TripEventType.OTP_ENTERED, time, stopId, matched, null, time);
        ReflectionTestUtils.setField(e, "id", id);
        return e;
    }

    public static TripEvent escortEvent(Long id, Long tripId, TripEventType type, Instant time) {
        TripEvent e = new TripEvent(tripId, type, time, null, null, null, time);
        ReflectionTestUtils.setField(e, "id", id);
        return e;
    }
}

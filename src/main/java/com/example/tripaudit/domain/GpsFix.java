package com.example.tripaudit.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

/** One GPS ping from the cab. Append-only raw fact. */
@Entity
@Table(name = "gps_fix",
        uniqueConstraints = @UniqueConstraint(name = "uk_gps_fix_trip_time", columnNames = {"trip_id", "recorded_at"}))
public class GpsFix {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "trip_id", nullable = false)
    private Long tripId;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    @Column(nullable = false)
    private double lat;

    @Column(nullable = false)
    private double lon;

    @Column(name = "speed_kmph", nullable = false)
    private double speedKmph;

    protected GpsFix() { } // required by JPA

    public GpsFix(Long tripId, Instant recordedAt, double lat, double lon, double speedKmph) {
        this.tripId = tripId;
        this.recordedAt = recordedAt;
        this.lat = lat;
        this.lon = lon;
        this.speedKmph = speedKmph;
    }

    public Long getId() { return id; }
    public Long getTripId() { return tripId; }
    public Instant getRecordedAt() { return recordedAt; }
    public double getLat() { return lat; }
    public double getLon() { return lon; }
    public double getSpeedKmph() { return speedKmph; }
}

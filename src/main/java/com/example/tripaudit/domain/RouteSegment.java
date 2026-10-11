package com.example.tripaudit.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/** One piece of the planned route polyline: start point -> end point, with its own speed limit. */
@Entity
@Table(name = "route_segment",
        uniqueConstraints = @UniqueConstraint(name = "uk_route_segment_trip_seq", columnNames = {"trip_id", "seq"}))
public class RouteSegment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "trip_id", nullable = false)
    private Long tripId;

    // 0..m-1, unique per trip.
    @Column(name = "seq", nullable = false)
    private int seq;

    @Column(name = "start_lat", nullable = false)
    private double startLat;

    @Column(name = "start_lon", nullable = false)
    private double startLon;

    @Column(name = "end_lat", nullable = false)
    private double endLat;

    @Column(name = "end_lon", nullable = false)
    private double endLon;

    @Column(name = "speed_limit_kmph", nullable = false)
    private double speedLimitKmph;

    protected RouteSegment() { } // required by JPA

    public RouteSegment(Long tripId, int seq, GeoPoint start, GeoPoint end, double speedLimitKmph) {
        this.tripId = tripId;
        this.seq = seq;
        this.startLat = start.lat();
        this.startLon = start.lon();
        this.endLat = end.lat();
        this.endLon = end.lon();
        this.speedLimitKmph = speedLimitKmph;
    }

    public GeoPoint start() { return new GeoPoint(startLat, startLon); }
    public GeoPoint end() { return new GeoPoint(endLat, endLon); }

    public Long getId() { return id; }
    public Long getTripId() { return tripId; }
    public int getSeq() { return seq; }
    public double getStartLat() { return startLat; }
    public double getStartLon() { return startLon; }
    public double getEndLat() { return endLat; }
    public double getEndLon() { return endLon; }
    public double getSpeedLimitKmph() { return speedLimitKmph; }
}

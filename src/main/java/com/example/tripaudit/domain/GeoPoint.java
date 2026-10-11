package com.example.tripaudit.domain;

/** A latitude/longitude pair in degrees. Immutable value object, not stored on its own. */
public record GeoPoint(double lat, double lon) { }

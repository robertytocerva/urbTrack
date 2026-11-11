package mx.itsu.emergencias.routing.service;

import mx.itsu.emergencias.common.geo.GeoPoint;

import java.util.List;

public record OsrmRoute(List<GeoPoint> coordinates, double distanceMeters, double durationSeconds) {
}

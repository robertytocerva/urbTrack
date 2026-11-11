package mx.itsu.emergencias.routing.service;

import mx.itsu.emergencias.common.geo.GeoPoint;

import java.util.UUID;

public record RouteContext(UUID alertId, GeoPoint origin, GeoPoint destination) {
}

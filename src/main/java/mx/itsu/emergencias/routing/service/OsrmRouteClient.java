package mx.itsu.emergencias.routing.service;

import mx.itsu.emergencias.common.geo.GeoPoint;

import java.util.Optional;

public interface OsrmRouteClient {
    Optional<OsrmRoute> findRoute(GeoPoint origin, GeoPoint destination);
}

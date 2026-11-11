package mx.itsu.emergencias.routing.api;

import mx.itsu.emergencias.common.geo.GeoPoint;

import java.util.List;
import java.util.UUID;

public record RouteResponse(
        UUID alertId,
        List<GeoPoint> coordinates,
        double distanceMeters,
        Double durationSeconds,
        boolean fallback
) {
}

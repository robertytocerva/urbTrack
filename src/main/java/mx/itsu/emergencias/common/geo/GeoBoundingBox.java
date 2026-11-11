package mx.itsu.emergencias.common.geo;

public record GeoBoundingBox(
        double minLatitude,
        double maxLatitude,
        double minLongitude,
        double maxLongitude,
        boolean allLongitudes,
        boolean crossesAntimeridian
) {
    public static final double EARTH_RADIUS_METERS = 6_371_000.0;

    public static GeoBoundingBox around(double latitude, double longitude, double radiusMeters) {
        double angularDistance = radiusMeters / EARTH_RADIUS_METERS;
        double latitudeDelta = Math.toDegrees(angularDistance);
        double minLatitude = Math.max(-90.0, latitude - latitudeDelta);
        double maxLatitude = Math.min(90.0, latitude + latitudeDelta);

        if (angularDistance >= Math.PI || minLatitude <= -90.0 || maxLatitude >= 90.0) {
            return new GeoBoundingBox(minLatitude, maxLatitude, -180.0, 180.0, true, false);
        }

        double longitudeDelta = Math.toDegrees(Math.asin(Math.min(1.0,
                Math.sin(angularDistance) / Math.cos(Math.toRadians(latitude)))));
        if (longitudeDelta >= 180.0) {
            return new GeoBoundingBox(minLatitude, maxLatitude, -180.0, 180.0, true, false);
        }

        double rawMinLongitude = longitude - longitudeDelta;
        double rawMaxLongitude = longitude + longitudeDelta;
        boolean crossesAntimeridian = rawMinLongitude < -180.0 || rawMaxLongitude > 180.0;
        double minLongitude = rawMinLongitude < -180.0 ? rawMinLongitude + 360.0 : rawMinLongitude;
        double maxLongitude = rawMaxLongitude > 180.0 ? rawMaxLongitude - 360.0 : rawMaxLongitude;
        return new GeoBoundingBox(minLatitude, maxLatitude, minLongitude, maxLongitude,
                false, crossesAntimeridian);
    }
}

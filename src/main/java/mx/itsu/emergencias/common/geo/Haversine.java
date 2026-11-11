package mx.itsu.emergencias.common.geo;

public final class Haversine {

    private Haversine() {
    }

    public static double distanceMeters(GeoPoint origin, GeoPoint destination) {
        double latitudeDelta = Math.toRadians(destination.latitude() - origin.latitude());
        double longitudeDelta = Math.toRadians(destination.longitude() - origin.longitude());
        double originLatitude = Math.toRadians(origin.latitude());
        double destinationLatitude = Math.toRadians(destination.latitude());
        double haversine = Math.pow(Math.sin(latitudeDelta / 2), 2)
                + Math.cos(originLatitude) * Math.cos(destinationLatitude)
                * Math.pow(Math.sin(longitudeDelta / 2), 2);
        return 2 * GeoBoundingBox.EARTH_RADIUS_METERS
                * Math.asin(Math.sqrt(Math.min(1.0, haversine)));
    }
}

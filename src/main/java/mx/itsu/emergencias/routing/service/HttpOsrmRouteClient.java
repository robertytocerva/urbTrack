package mx.itsu.emergencias.routing.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import mx.itsu.emergencias.common.geo.GeoPoint;
import mx.itsu.emergencias.routing.config.RoutingProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

@Component
public class HttpOsrmRouteClient implements OsrmRouteClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(HttpOsrmRouteClient.class);

    private final HttpClient httpClient;
    private final RoutingProperties properties;
    private final ObjectMapper objectMapper;

    public HttpOsrmRouteClient(RoutingProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(properties.connectTimeoutMs()))
                .build();
    }

    @Override
    public Optional<OsrmRoute> findRoute(GeoPoint origin, GeoPoint destination) {
        String baseUrl = properties.osrmBaseUrl().replaceAll("/+$", "");
        String coordinates = origin.longitude() + "," + origin.latitude() + ";"
                + destination.longitude() + "," + destination.latitude();
        URI uri = URI.create(baseUrl + "/route/v1/driving/" + coordinates
                + "?overview=full&geometries=geojson");
        try {
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofMillis(properties.readTimeoutMs()))
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            HttpResponse<String> httpResponse = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (httpResponse.statusCode() < 200 || httpResponse.statusCode() >= 300) {
                LOGGER.warn("OSRM returned HTTP {}; using straight-line fallback", httpResponse.statusCode());
                return Optional.empty();
            }
            String body = httpResponse.body();
            OsrmResponse response = body == null ? null : objectMapper.readValue(body, OsrmResponse.class);
            if (response == null || !"Ok".equals(response.code())
                    || response.routes() == null || response.routes().isEmpty()) {
                LOGGER.warn("OSRM returned no usable route (code={})", response == null ? "empty" : response.code());
                return Optional.empty();
            }
            OsrmRoutePayload route = response.routes().getFirst();
            if (!Double.isFinite(route.distance()) || route.distance() < 0
                    || !Double.isFinite(route.duration()) || route.duration() < 0
                    || route.geometry() == null || route.geometry().coordinates() == null
                    || route.geometry().coordinates().size() < 2) {
                LOGGER.warn("OSRM route response was incomplete; using straight-line fallback");
                return Optional.empty();
            }
            List<GeoPoint> points = route.geometry().coordinates().stream()
                    .map(this::toPoint)
                    .toList();
            if (points.stream().anyMatch(point -> point == null)) return Optional.empty();
            return Optional.of(new OsrmRoute(points, route.distance(), route.duration()));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            LOGGER.warn("OSRM request interrupted; using straight-line fallback");
            return Optional.empty();
        } catch (IOException | JacksonException | IllegalArgumentException exception) {
            String detail = exception.getMessage();
            if (detail != null) detail = detail.replaceAll("https?://[^\\s)]+", "[URL]");
            LOGGER.warn("OSRM request failed; using straight-line fallback ({}: {})",
                    exception.getClass().getSimpleName(), detail);
            return Optional.empty();
        }
    }

    private GeoPoint toPoint(List<Double> coordinate) {
        if (coordinate == null || coordinate.size() < 2) return null;
        double longitude = coordinate.get(0);
        double latitude = coordinate.get(1);
        if (!Double.isFinite(latitude) || latitude < -90 || latitude > 90
                || !Double.isFinite(longitude) || longitude < -180 || longitude > 180) return null;
        return new GeoPoint(latitude, longitude);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record OsrmResponse(String code, List<OsrmRoutePayload> routes) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record OsrmRoutePayload(double distance, double duration, OsrmGeometry geometry) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record OsrmGeometry(List<List<Double>> coordinates) {
    }
}

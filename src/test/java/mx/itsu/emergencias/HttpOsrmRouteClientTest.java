package mx.itsu.emergencias;

import com.sun.net.httpserver.HttpServer;
import mx.itsu.emergencias.common.geo.GeoPoint;
import mx.itsu.emergencias.routing.config.RoutingProperties;
import mx.itsu.emergencias.routing.service.HttpOsrmRouteClient;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpOsrmRouteClientTest {

    @Test
    void parsesOsrmGeoJsonRoute() throws Exception {
        String response = """
                {"code":"Ok","routes":[{"legs":[{"distance":27.1,"duration":3.9,"steps":[]}],"distance":27.1,"duration":3.9,"weight_name":"routability","weight":3.9,"geometry":{"type":"LineString","coordinates":[[-102.066336,19.416854],[-102.066410,19.416619]]}}],"waypoints":[]}
                """;
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/route/v1/driving/", exchange -> {
            byte[] payload = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, payload.length);
            try (var output = exchange.getResponseBody()) {
                output.write(payload);
            }
        });
        server.start();
        try {
            var client = new HttpOsrmRouteClient(new RoutingProperties(
                    "http://127.0.0.1:" + server.getAddress().getPort(), 1000, 1000),
                    JsonMapper.builder().build());
            var route = client.findRoute(new GeoPoint(19.4169, -102.0665),
                    new GeoPoint(19.4167, -102.0667));

            assertTrue(route.isPresent());
            assertEquals(27.1, route.get().distanceMeters());
            assertEquals(3.9, route.get().durationSeconds());
            assertEquals(new GeoPoint(19.416854, -102.066336), route.get().coordinates().getFirst());
        } finally {
            server.stop(0);
        }
    }
}

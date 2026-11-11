package mx.itsu.emergencias;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class AuthIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("DB_URL", POSTGRES::getJdbcUrl);
        registry.add("DB_MIGRATION_URL", POSTGRES::getJdbcUrl);
        registry.add("DB_USER", POSTGRES::getUsername);
        registry.add("DB_PASSWORD", POSTGRES::getPassword);
        registry.add("JWT_SECRET", () -> "test-only-jwt-secret-with-at-least-32-bytes");
    }

    @LocalServerPort
    private int port;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void flywayAppliesVersionOneSchema() {
        String appliedVersion = jdbcTemplate.queryForObject(
                "select version from flyway_schema_history where success = true order by installed_rank desc limit 1",
                String.class);
        Integer usersTable = jdbcTemplate.queryForObject(
                "select count(*) from information_schema.tables where table_schema = 'public' and table_name = 'users'",
                Integer.class);
        Integer unitsTable = jdbcTemplate.queryForObject(
                "select count(*) from information_schema.tables where table_schema = 'public' and table_name = 'units'",
                Integer.class);

        assertEquals("1", appliedVersion);
        assertEquals(1, usersTable);
        assertEquals(1, unitsTable);
    }

    @Test
    void actuatorHealthIsAvailableAndUnauthenticatedMeIsRejected() throws Exception {
        HttpResponse<String> health = request("GET", "/actuator/health", null, null);
        assertEquals(200, health.statusCode());
        assertTrue(health.body().contains("\"status\":\"UP\""));

        HttpResponse<String> profile = request("GET", "/api/me", null, null);
        assertEquals(401, profile.statusCode());
        assertTrue(profile.body().contains("\"code\":\"UNAUTHORIZED\""));
    }

    @Test
    void registersCitizenLogsInAndReadsProfile() throws Exception {
        String email = "citizen-" + UUID.randomUUID() + "@example.com";
        HttpResponse<String> registration = request("POST", "/api/auth/register/citizen", """
                {"email":"%s","password":"Secreto123!","fullName":"Ana Pérez","phone":"+524521234567"}
                """.formatted(email), null);
        assertEquals(201, registration.statusCode());
        assertTrue(registration.body().contains("\"role\":\"CITIZEN\""));

        HttpResponse<String> login = request("POST", "/api/auth/login", """
                {"email":"%s","password":"Secreto123!"}
                """.formatted(email), null);
        assertEquals(200, login.statusCode());
        assertTrue(login.body().contains("\"role\":\"CITIZEN\""));
        Matcher tokenMatcher = Pattern.compile("\\\"accessToken\\\":\\\"([^\\\"]+)\\\"").matcher(login.body());
        assertTrue(tokenMatcher.find(), "La respuesta de login debe incluir accessToken");
        String token = tokenMatcher.group(1);

        HttpResponse<String> profile = request("GET", "/api/me", null, token);
        assertEquals(200, profile.statusCode());
        assertTrue(profile.body().contains("\"email\":\"" + email + "\""));
        assertTrue(profile.body().contains("\"role\":\"CITIZEN\""));

        HttpResponse<String> forbidden = request("GET", "/api/units/me/status", null, token);
        assertEquals(403, forbidden.statusCode());
        assertTrue(forbidden.body().contains("\"code\":\"FORBIDDEN\""));
    }

    @Test
    void registersUnitWithOfflineStatus() throws Exception {
        String email = "unit-" + UUID.randomUUID() + "@example.com";
        HttpResponse<String> registration = request("POST", "/api/auth/register/unit", """
                {"email":"%s","password":"Secreto123!","fullName":"Unidad U-17","unitType":"POLICE","identifier":"U-%s"}
                """.formatted(email, UUID.randomUUID()), null);
        assertEquals(201, registration.statusCode());
        assertTrue(registration.body().contains("\"role\":\"UNIT\""));
        assertTrue(registration.body().contains("\"unitType\":\"POLICE\""));
        assertTrue(registration.body().contains("\"unitStatus\":\"OFFLINE\""));
    }

    @Test
    void unitCanChangeManualStatusAndLocationEventsAreIdempotentAndOrdered() throws Exception {
        UnitAccount account = registerUnitAndLogin();
        HttpResponse<String> statusResponse = request("PUT", "/api/units/me/status",
                "{\"status\":\"AVAILABLE\"}", account.token());
        assertEquals(200, statusResponse.statusCode());
        assertTrue(statusResponse.body().contains("\"status\":\"AVAILABLE\""));

        HttpResponse<String> busyStatus = request("PUT", "/api/units/me/status",
                "{\"status\":\"BUSY\"}", account.token());
        assertEquals(400, busyStatus.statusCode());
        assertTrue(busyStatus.body().contains("\"code\":\"VALIDATION_ERROR\""));

        UUID eventId = UUID.randomUUID();
        String currentLocation = locationJson(eventId, 19.4167, -102.0667, Instant.now().minusSeconds(5));
        HttpResponse<String> first = request("POST", "/api/units/me/location", currentLocation, account.token());
        assertEquals(200, first.statusCode());
        assertTrue(first.body().contains("\"duplicate\":false"));
        assertTrue(first.body().contains("\"currentLocationUpdated\":true"));

        HttpResponse<String> duplicate = request("POST", "/api/units/me/location", currentLocation, account.token());
        assertEquals(200, duplicate.statusCode());
        assertTrue(duplicate.body().contains("\"duplicate\":true"));
        assertEquals(1, jdbcTemplate.queryForObject(
                "select count(*) from unit_location_history where event_id = ?", Integer.class, eventId));

        HttpResponse<String> stale = request("POST", "/api/units/me/location",
                locationJson(UUID.randomUUID(), 35.0, 10.0, Instant.now().minusSeconds(600)), account.token());
        assertEquals(200, stale.statusCode());
        assertTrue(stale.body().contains("\"currentLocationUpdated\":false"));

        String citizenToken = registerCitizenAndLogin();
        HttpResponse<String> nearby = request("GET", "/api/units/nearby?lat=19.4167&lon=-102.0667&radiusKm=1",
                null, citizenToken);
        assertEquals(200, nearby.statusCode());
        assertTrue(nearby.body().contains(account.unitId().toString()));
        assertTrue(nearby.body().contains("\"unitType\":\"POLICE\""));
        assertTrue(!nearby.body().contains("userId") && !nearby.body().contains("identifier")
                && !nearby.body().contains("email") && !nearby.body().contains("fullName"));
    }

    @Test
    void batchLocationIsIdempotentAndValidatesCoordinatesAndCaptureTime() throws Exception {
        UnitAccount account = registerUnitAndLogin();
        UUID firstEvent = UUID.randomUUID();
        UUID secondEvent = UUID.randomUUID();
        String batch = "[%s,%s]".formatted(
                locationJson(firstEvent, 0.0, 0.0, Instant.now().minusSeconds(20)),
                locationJson(secondEvent, 0.1, 0.1, Instant.now().minusSeconds(10)));

        HttpResponse<String> first = request("POST", "/api/units/me/locations/batch", batch, account.token());
        assertEquals(200, first.statusCode());
        assertTrue(first.body().contains("\"received\":2"));
        assertTrue(first.body().contains("\"stored\":2"));
        assertTrue(first.body().contains("\"duplicates\":0"));

        HttpResponse<String> repeated = request("POST", "/api/units/me/locations/batch", batch, account.token());
        assertEquals(200, repeated.statusCode());
        assertTrue(repeated.body().contains("\"stored\":0"));
        assertTrue(repeated.body().contains("\"duplicates\":2"));

        HttpResponse<String> invalidCoordinates = request("POST", "/api/units/me/location",
                locationJson(UUID.randomUUID(), 90.1, 0.0, Instant.now()), account.token());
        assertEquals(400, invalidCoordinates.statusCode());

        HttpResponse<String> future = request("POST", "/api/units/me/location",
                locationJson(UUID.randomUUID(), 0.0, 0.0, Instant.now().plusSeconds(120)), account.token());
        assertEquals(400, future.statusCode());
        assertTrue(future.body().contains("\"code\":\"VALIDATION_ERROR\""));

        UUID mustNotPersist = UUID.randomUUID();
        String invalidBatch = "[%s,%s]".formatted(
                locationJson(mustNotPersist, 1.0, 1.0, Instant.now()),
                locationJson(UUID.randomUUID(), 2.0, 2.0, Instant.now().plusSeconds(120)));
        HttpResponse<String> rejectedBatch = request("POST", "/api/units/me/locations/batch",
                invalidBatch, account.token());
        assertEquals(400, rejectedBatch.statusCode());
        assertEquals(0, jdbcTemplate.queryForObject(
                "select count(*) from unit_location_history where event_id = ?", Integer.class, mustNotPersist));
    }

    private UnitAccount registerUnitAndLogin() throws Exception {
        String email = "phase2-unit-" + UUID.randomUUID() + "@example.com";
        HttpResponse<String> registration = request("POST", "/api/auth/register/unit", """
                {"email":"%s","password":"Secreto123!","fullName":"Unidad prueba","unitType":"POLICE","identifier":"P2-%s"}
                """.formatted(email, UUID.randomUUID()), null);
        assertEquals(201, registration.statusCode(), registration.body());
        Matcher unitIdMatcher = Pattern.compile("\\\"unitId\\\":\\\"([^\\\"]+)\\\"").matcher(registration.body());
        assertTrue(unitIdMatcher.find());

        HttpResponse<String> login = request("POST", "/api/auth/login", """
                {"email":"%s","password":"Secreto123!"}
                """.formatted(email), null);
        assertEquals(200, login.statusCode());
        return new UnitAccount(UUID.fromString(unitIdMatcher.group(1)), extractToken(login.body()));
    }

    private String registerCitizenAndLogin() throws Exception {
        String email = "phase2-citizen-" + UUID.randomUUID() + "@example.com";
        HttpResponse<String> registration = request("POST", "/api/auth/register/citizen", """
                {"email":"%s","password":"Secreto123!","fullName":"Ciudadana prueba"}
                """.formatted(email), null);
        assertEquals(201, registration.statusCode());
        HttpResponse<String> login = request("POST", "/api/auth/login", """
                {"email":"%s","password":"Secreto123!"}
                """.formatted(email), null);
        assertEquals(200, login.statusCode());
        return extractToken(login.body());
    }

    private String extractToken(String body) {
        Matcher tokenMatcher = Pattern.compile("\\\"accessToken\\\":\\\"([^\\\"]+)\\\"").matcher(body);
        assertTrue(tokenMatcher.find(), "La respuesta de login debe incluir accessToken");
        return tokenMatcher.group(1);
    }

    private String locationJson(UUID eventId, double latitude, double longitude, Instant recordedAt) {
        return "{\"eventId\":\"%s\",\"latitude\":%s,\"longitude\":%s,\"recordedAt\":\"%s\"}"
                .formatted(eventId, latitude, longitude, recordedAt);
    }

    private record UnitAccount(UUID unitId, String token) {
    }

    private HttpResponse<String> request(String method, String path, String body, String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path));
        if (body != null) {
            builder.header("Content-Type", "application/json");
        }
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        HttpRequest.BodyPublisher publisher = body == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body);
        return httpClient.send(builder.method(method, publisher).build(), HttpResponse.BodyHandlers.ofString());
    }
}

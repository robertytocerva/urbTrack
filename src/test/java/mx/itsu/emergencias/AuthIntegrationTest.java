package mx.itsu.emergencias;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import mx.itsu.emergencias.dispatch.service.DispatchExpirationScheduler;
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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

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

    @Autowired
    private DispatchExpirationScheduler dispatchExpirationScheduler;

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

    @Test
    void concurrentDispatchAcceptanceHasExactlyOneWinnerAndCompletesAlertLifecycle() throws Exception {
        double latitude = -40.0;
        double longitude = 80.0;
        List<UnitAccount> units = List.of(
                registerAvailableUnit("POLICE", latitude, longitude),
                registerAvailableUnit("POLICE", latitude, longitude),
                registerAvailableUnit("POLICE", latitude, longitude));
        String citizenToken = registerCitizenAndLogin();
        HttpResponse<String> created = createAlert(citizenToken, "ROBBERY", latitude, longitude);
        assertEquals(201, created.statusCode(), created.body());
        UUID alertId = UUID.fromString(jsonValue(created.body(), "id"));
        assertTrue(created.body().contains("\"requiredUnitType\":\"POLICE\""));

        HttpResponse<String> alreadyActive = createAlert(citizenToken, "FIRE", latitude, longitude);
        assertEquals(409, alreadyActive.statusCode());
        assertTrue(alreadyActive.body().contains("\"code\":\"ACTIVE_ALERT_EXISTS\""));

        List<String> dispatchIds = new ArrayList<>();
        for (UnitAccount unit : units) {
            HttpResponse<String> pending = request("GET", "/api/dispatches/pending", null, unit.token());
            assertEquals(200, pending.statusCode());
            Matcher matcher = Pattern.compile("\\\"dispatchId\\\":\\\"([^\\\"]+)\\\"").matcher(pending.body());
            assertTrue(matcher.find(), "Cada unidad debe tener una oferta pendiente");
            dispatchIds.add(matcher.group(1));
        }

        ExecutorService pool = Executors.newFixedThreadPool(units.size());
        CountDownLatch ready = new CountDownLatch(units.size());
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> outcomes = new ArrayList<>();
        try {
            for (int index = 0; index < units.size(); index++) {
                UnitAccount unit = units.get(index);
                String dispatchId = dispatchIds.get(index);
                outcomes.add(pool.submit(() -> {
                    ready.countDown();
                    if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("barrier timeout");
                    return request("POST", "/api/dispatches/" + dispatchId + "/accept", null,
                            unit.token()).statusCode();
                }));
            }
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> outcome : outcomes) statuses.add(outcome.get(20, TimeUnit.SECONDS));
            assertEquals(1, statuses.stream().filter(status -> status == 200).count());
            assertEquals(2, statuses.stream().filter(status -> status == 409).count());

            assertEquals(1, jdbcTemplate.queryForObject(
                    "select count(*) from alerts where id = ? and status = 'EN_ROUTE'", Integer.class, alertId));
            assertEquals(1, jdbcTemplate.queryForObject(
                    "select count(*) from alert_dispatches where alert_id = ? and status = 'ACCEPTED'",
                    Integer.class, alertId));
            assertEquals(2, jdbcTemplate.queryForObject(
                    "select count(*) from alert_dispatches where alert_id = ? and status = 'CANCELLED'",
                    Integer.class, alertId));

            int winner = statuses.indexOf(200);
            String winningToken = units.get(winner).token();
            HttpResponse<String> active = request("GET", "/api/alerts/active", null, citizenToken);
            assertEquals(200, active.statusCode());
            assertTrue(active.body().contains("\"status\":\"EN_ROUTE\""));

            HttpResponse<String> arrived = request("POST", "/api/alerts/" + alertId + "/arrive", null,
                    winningToken);
            assertEquals(200, arrived.statusCode());
            assertTrue(arrived.body().contains("\"status\":\"ON_SCENE\""));
            HttpResponse<String> resolved = request("POST", "/api/alerts/" + alertId + "/resolve", null,
                    winningToken);
            assertEquals(200, resolved.statusCode());
            assertTrue(resolved.body().contains("\"status\":\"RESOLVED\""));
            assertEquals(1, jdbcTemplate.queryForObject(
                    "select count(*) from alert_events where alert_id = ? and event_type = 'ALERT_ACCEPTED'",
                    Integer.class, alertId));
            assertEquals(1, jdbcTemplate.queryForObject(
                    "select count(*) from alert_events where alert_id = ? and event_type = 'ALERT_ARRIVED'",
                    Integer.class, alertId));
            assertEquals(1, jdbcTemplate.queryForObject(
                    "select count(*) from alert_events where alert_id = ? and event_type = 'ALERT_RESOLVED'",
                    Integer.class, alertId));
            assertEquals(1, jdbcTemplate.queryForObject(
                    "select count(*) from units where id = ? and status = 'AVAILABLE'", Integer.class,
                    UUID.fromString(jsonValue(resolved.body(), "unitId"))));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void dispatchExpandsRadiusAfterRejectionAndScheduledExpirationExpiresAlert() throws Exception {
        double alertLatitude = 70.0;
        double alertLongitude = 140.0;
        UnitAccount fireUnit = registerAvailableUnit("FIRE", 70.05, alertLongitude);
        String citizenToken = registerCitizenAndLogin();
        HttpResponse<String> created = createAlert(citizenToken, "FIRE", alertLatitude, alertLongitude);
        assertEquals(201, created.statusCode());
        UUID alertId = UUID.fromString(jsonValue(created.body(), "id"));
        assertTrue(created.body().contains("\"dispatchRound\":2"));

        HttpResponse<String> pending = request("GET", "/api/dispatches/pending", null, fireUnit.token());
        String dispatchId = jsonValue(pending.body(), "dispatchId");
        HttpResponse<String> rejected = request("POST", "/api/dispatches/" + dispatchId + "/reject", null,
                fireUnit.token());
        assertEquals(200, rejected.statusCode());
        assertTrue(rejected.body().contains("\"status\":\"REJECTED\""));
        assertEquals(1, jdbcTemplate.queryForObject(
                "select count(*) from alerts where id = ? and status = 'EXPIRED' and dispatch_round = 3",
                Integer.class, alertId));

        double secondLat = 45.0;
        double secondLon = 45.0;
        UnitAccount policeUnit = registerAvailableUnit("POLICE", secondLat, secondLon);
        HttpResponse<String> secondAlert = createAlert(citizenToken, "ROBBERY", secondLat, secondLon);
        assertEquals(201, secondAlert.statusCode());
        UUID secondAlertId = UUID.fromString(jsonValue(secondAlert.body(), "id"));
        HttpResponse<String> secondPending = request("GET", "/api/dispatches/pending", null, policeUnit.token());
        String secondDispatchId = jsonValue(secondPending.body(), "dispatchId");
        jdbcTemplate.update("update alert_dispatches set expires_at = now() - interval '1 second' where id = ?",
                UUID.fromString(secondDispatchId));
        dispatchExpirationScheduler.expireAndAdvance();

        assertEquals(1, jdbcTemplate.queryForObject(
                "select count(*) from alert_dispatches where id = ? and status = 'EXPIRED'",
                Integer.class, UUID.fromString(secondDispatchId)));
        assertEquals(1, jdbcTemplate.queryForObject(
                "select count(*) from alerts where id = ? and status = 'EXPIRED'",
                Integer.class, secondAlertId));
    }

    @Test
    void citizenCanCancelPendingAlertAndRequiredUnitTypeMappingIsApplied() throws Exception {
        UnitAccount civilUnit = registerAvailableUnit("CIVIL_PROTECTION", 55.0, 20.0);
        String citizenToken = registerCitizenAndLogin();
        HttpResponse<String> created = createAlert(citizenToken, "OTHER", 55.0, 20.0);
        assertEquals(201, created.statusCode());
        assertTrue(created.body().contains("\"requiredUnitType\":\"CIVIL_PROTECTION\""));
        UUID alertId = UUID.fromString(jsonValue(created.body(), "id"));

        HttpResponse<String> forbidden = request("POST", "/api/alerts/" + alertId + "/cancel", null,
                civilUnit.token());
        assertEquals(403, forbidden.statusCode());

        HttpResponse<String> cancelled = request("POST", "/api/alerts/" + alertId + "/cancel", null,
                citizenToken);
        assertEquals(200, cancelled.statusCode());
        assertTrue(cancelled.body().contains("\"status\":\"CANCELLED\""));
        assertEquals(1, jdbcTemplate.queryForObject(
                "select count(*) from alert_dispatches where alert_id = ? and status = 'CANCELLED'",
                Integer.class, alertId));
        assertEquals(1, jdbcTemplate.queryForObject(
                "select count(*) from alert_events where alert_id = ? and event_type = 'ALERT_CANCELLED'",
                Integer.class, alertId));
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

    private UnitAccount registerAvailableUnit(String type, double latitude, double longitude) throws Exception {
        String email = "phase3-unit-" + UUID.randomUUID() + "@example.com";
        String identifier = "P3-" + UUID.randomUUID();
        HttpResponse<String> registration = request("POST", "/api/auth/register/unit", """
                {"email":"%s","password":"Secreto123!","fullName":"Unidad para alertas","unitType":"%s","identifier":"%s"}
                """.formatted(email, type, identifier), null);
        assertEquals(201, registration.statusCode(), registration.body());
        UUID unitId = UUID.fromString(jsonValue(registration.body(), "unitId"));
        HttpResponse<String> login = request("POST", "/api/auth/login", """
                {"email":"%s","password":"Secreto123!"}
                """.formatted(email), null);
        assertEquals(200, login.statusCode());
        String token = extractToken(login.body());
        assertEquals(200, request("PUT", "/api/units/me/status", "{\"status\":\"AVAILABLE\"}", token)
                .statusCode());
        assertEquals(200, request("POST", "/api/units/me/location",
                locationJson(UUID.randomUUID(), latitude, longitude, Instant.now().minusSeconds(5)), token)
                .statusCode());
        return new UnitAccount(unitId, token);
    }

    private HttpResponse<String> createAlert(String citizenToken, String type, double latitude, double longitude)
            throws Exception {
        return request("POST", "/api/alerts", """
                {"type":"%s","description":"Alerta de prueba","latitude":%s,"longitude":%s}
                """.formatted(type, latitude, longitude), citizenToken);
    }

    private String jsonValue(String body, String field) {
        Matcher matcher = Pattern.compile("\\\"" + Pattern.quote(field) + "\\\":\\\"([^\\\"]+)\\\"")
                .matcher(body);
        assertTrue(matcher.find(), "La respuesta debe incluir " + field + ": " + body);
        return matcher.group(1);
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

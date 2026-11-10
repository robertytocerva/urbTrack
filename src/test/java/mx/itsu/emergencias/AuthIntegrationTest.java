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

package mx.itsu.emergencias.alert.repo;

import mx.itsu.emergencias.common.exception.InvalidRequestException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.UUID;

@Repository
public class AlertEventRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public AlertEventRepository(NamedParameterJdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public void append(UUID alertId, String eventType, UUID actorUserId, Map<String, Object> payload) {
        String json;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (JacksonException exception) {
            throw new InvalidRequestException("No se pudo serializar el evento de alerta");
        }
        jdbcTemplate.update("""
                INSERT INTO alert_events (alert_id, event_type, actor_user_id, payload)
                VALUES (:alertId, :eventType, :actorUserId, CAST(:payload AS jsonb))
                """, new MapSqlParameterSource()
                .addValue("alertId", alertId)
                .addValue("eventType", eventType)
                .addValue("actorUserId", actorUserId)
                .addValue("payload", json));
    }
}

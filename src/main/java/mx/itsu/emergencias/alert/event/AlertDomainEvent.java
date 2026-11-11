package mx.itsu.emergencias.alert.event;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record AlertDomainEvent(
        UUID alertId,
        String eventType,
        UUID actorUserId,
        Map<String, Object> payload,
        Instant occurredAt
) {
}

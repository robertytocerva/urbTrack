package mx.itsu.emergencias.realtime.api;

import mx.itsu.emergencias.alert.api.AlertResponse;

import java.time.Instant;
import java.util.UUID;

public record AlertStatusNotification(UUID alertId, String eventType, AlertResponse alert, Instant occurredAt) {
}

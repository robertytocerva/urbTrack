package mx.itsu.emergencias.realtime.api;

import mx.itsu.emergencias.dispatch.domain.DispatchStatus;

import java.util.UUID;

public record DispatchUpdateNotification(UUID dispatchId, UUID alertId, DispatchStatus status, String reason) {
}

package mx.itsu.emergencias.dispatch.api;

import mx.itsu.emergencias.dispatch.domain.DispatchStatus;

import java.util.UUID;

public record DispatchActionResponse(UUID dispatchId, UUID alertId, DispatchStatus status) {
}

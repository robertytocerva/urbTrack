package mx.itsu.emergencias.dispatch.repo;

import java.util.UUID;

public record DispatchExpirationRef(UUID dispatchId, UUID alertId) {
}

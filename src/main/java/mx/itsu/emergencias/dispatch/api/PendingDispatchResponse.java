package mx.itsu.emergencias.dispatch.api;

import mx.itsu.emergencias.alert.domain.AlertType;
import mx.itsu.emergencias.unit.domain.UnitType;

import java.time.Instant;
import java.util.UUID;

public record PendingDispatchResponse(
        UUID dispatchId,
        UUID alertId,
        AlertType type,
        UnitType requiredUnitType,
        String description,
        double latitude,
        double longitude,
        short round,
        double distanceMeters,
        Instant expiresAt
) {
}

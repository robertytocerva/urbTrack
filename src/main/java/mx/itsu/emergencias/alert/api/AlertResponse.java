package mx.itsu.emergencias.alert.api;

import mx.itsu.emergencias.alert.domain.AlertStatus;
import mx.itsu.emergencias.alert.domain.AlertType;
import mx.itsu.emergencias.unit.domain.UnitType;

import java.time.Instant;
import java.util.UUID;

public record AlertResponse(
        UUID id,
        AlertType type,
        UnitType requiredUnitType,
        String description,
        double latitude,
        double longitude,
        AlertStatus status,
        short dispatchRound,
        Instant createdAt,
        Instant acceptedAt,
        Instant arrivedAt,
        Instant resolvedAt,
        Instant cancelledAt,
        AssignedUnitResponse assignedUnit
) {
}

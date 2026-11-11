package mx.itsu.emergencias.alert.api;

import mx.itsu.emergencias.unit.domain.UnitType;

import java.util.UUID;

public record AssignedUnitResponse(
        UUID unitId,
        UnitType unitType,
        String identifier,
        Double latitude,
        Double longitude,
        Double heading
) {
}

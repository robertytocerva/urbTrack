package mx.itsu.emergencias.unit.api;

import mx.itsu.emergencias.unit.domain.UnitType;

import java.util.UUID;

public record NearbyUnitResponse(
        UUID unitId,
        UnitType unitType,
        double latitude,
        double longitude,
        Double heading
) {
}

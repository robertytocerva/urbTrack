package mx.itsu.emergencias.unit.api;

import mx.itsu.emergencias.unit.domain.UnitStatus;

import java.util.UUID;

public record UnitStatusResponse(UUID unitId, UnitStatus status) {
}

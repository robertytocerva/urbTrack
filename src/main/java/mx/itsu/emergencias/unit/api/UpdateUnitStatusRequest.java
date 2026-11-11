package mx.itsu.emergencias.unit.api;

import jakarta.validation.constraints.NotNull;
import mx.itsu.emergencias.unit.domain.UnitStatus;

public record UpdateUnitStatusRequest(@NotNull UnitStatus status) {
}

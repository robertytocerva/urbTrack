package mx.itsu.emergencias.tracking.api;

import java.util.UUID;

public record UnitLocationResult(UUID eventId, boolean duplicate, boolean currentLocationUpdated) {
}

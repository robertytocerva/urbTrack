package mx.itsu.emergencias.realtime.api;

import java.time.Instant;
import java.util.UUID;

public record UnitLocationUpdate(
        UUID alertId,
        UUID unitId,
        double latitude,
        double longitude,
        Double heading,
        Double speedKmh,
        Instant recordedAt
) {
}

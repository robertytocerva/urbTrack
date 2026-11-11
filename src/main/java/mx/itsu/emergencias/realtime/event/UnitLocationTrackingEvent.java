package mx.itsu.emergencias.realtime.event;

import java.time.Instant;
import java.util.UUID;

public record UnitLocationTrackingEvent(
        UUID alertId,
        UUID unitId,
        double latitude,
        double longitude,
        Double heading,
        Double speedKmh,
        Instant recordedAt
) {
}

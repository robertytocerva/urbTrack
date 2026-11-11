package mx.itsu.emergencias.tracking.api;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

public record UnitLocationRequest(
        @NotNull UUID eventId,
        @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") Double latitude,
        @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") Double longitude,
        @DecimalMin("0.0") @DecimalMax("360.0") Double heading,
        Double speedKmh,
        @NotNull Instant recordedAt
) {
}

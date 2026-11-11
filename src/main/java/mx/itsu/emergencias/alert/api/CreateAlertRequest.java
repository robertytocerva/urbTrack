package mx.itsu.emergencias.alert.api;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import mx.itsu.emergencias.alert.domain.AlertType;

public record CreateAlertRequest(
        @NotNull AlertType type,
        @Size(max = 500) String description,
        @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") Double latitude,
        @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") Double longitude
) {
}

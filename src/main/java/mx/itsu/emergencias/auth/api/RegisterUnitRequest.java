package mx.itsu.emergencias.auth.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import mx.itsu.emergencias.unit.domain.UnitType;

public record RegisterUnitRequest(
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(min = 8, max = 72) String password,
        @NotBlank @Size(min = 2, max = 150) String fullName,
        @Size(max = 30) String phone,
        @NotNull UnitType unitType,
        @NotBlank @Size(max = 50) String identifier
) {
}

package mx.itsu.emergencias.auth.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterCitizenRequest(
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(min = 8, max = 72) String password,
        @NotBlank @Size(min = 2, max = 150) String fullName,
        @Size(max = 30) String phone
) {
}

package mx.itsu.emergencias.auth.api;

import mx.itsu.emergencias.user.domain.Role;

import java.util.UUID;

public record LoginResponse(String accessToken, Role role, UUID userId) {
}

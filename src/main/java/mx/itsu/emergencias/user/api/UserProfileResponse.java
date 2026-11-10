package mx.itsu.emergencias.user.api;

import mx.itsu.emergencias.unit.domain.UnitStatus;
import mx.itsu.emergencias.unit.domain.UnitType;
import mx.itsu.emergencias.user.domain.Role;

import java.util.UUID;

public record UserProfileResponse(
        UUID userId,
        String email,
        String fullName,
        String phone,
        Role role,
        UUID unitId,
        UnitType unitType,
        String identifier,
        UnitStatus unitStatus
) {
}

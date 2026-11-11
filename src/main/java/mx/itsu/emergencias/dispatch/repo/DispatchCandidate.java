package mx.itsu.emergencias.dispatch.repo;

import java.util.UUID;

public record DispatchCandidate(UUID unitId, double distanceMeters) {
}

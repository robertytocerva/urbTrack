package mx.itsu.emergencias.alert.service;

import mx.itsu.emergencias.alert.api.AlertResponse;
import mx.itsu.emergencias.alert.api.AssignedUnitResponse;
import mx.itsu.emergencias.alert.domain.AlertEntity;
import mx.itsu.emergencias.unit.domain.UnitEntity;
import mx.itsu.emergencias.unit.domain.UnitLocationEntity;
import mx.itsu.emergencias.unit.repo.UnitLocationRepository;
import org.springframework.stereotype.Component;

@Component
public class AlertResponseMapper {

    private final UnitLocationRepository locationRepository;

    public AlertResponseMapper(UnitLocationRepository locationRepository) {
        this.locationRepository = locationRepository;
    }

    public AlertResponse toResponse(AlertEntity alert) {
        AssignedUnitResponse assignedUnit = null;
        UnitEntity unit = alert.getAssignedUnit();
        if (unit != null) {
            UnitLocationEntity location = locationRepository.findById(unit.getId()).orElse(null);
            assignedUnit = new AssignedUnitResponse(
                    unit.getId(), unit.getUnitType(), unit.getIdentifier(),
                    location == null ? null : location.getLatitude(),
                    location == null ? null : location.getLongitude(),
                    location == null ? null : location.getHeading());
        }
        return new AlertResponse(alert.getId(), alert.getType(), alert.getRequiredUnitType(),
                alert.getDescription(), alert.getLatitude(), alert.getLongitude(), alert.getStatus(),
                alert.getDispatchRound(), alert.getCreatedAt(), alert.getAcceptedAt(), alert.getArrivedAt(),
                alert.getResolvedAt(), alert.getCancelledAt(), assignedUnit);
    }
}

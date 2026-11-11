package mx.itsu.emergencias.routing.service;

import mx.itsu.emergencias.alert.domain.AlertEntity;
import mx.itsu.emergencias.alert.domain.AlertStatus;
import mx.itsu.emergencias.alert.repo.AlertRepository;
import mx.itsu.emergencias.common.exception.ConflictException;
import mx.itsu.emergencias.common.exception.ForbiddenException;
import mx.itsu.emergencias.common.exception.NotFoundException;
import mx.itsu.emergencias.common.geo.GeoPoint;
import mx.itsu.emergencias.unit.domain.UnitLocationEntity;
import mx.itsu.emergencias.unit.repo.UnitLocationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class AlertRouteContextService {

    private final AlertRepository alertRepository;
    private final UnitLocationRepository locationRepository;

    public AlertRouteContextService(AlertRepository alertRepository, UnitLocationRepository locationRepository) {
        this.alertRepository = alertRepository;
        this.locationRepository = locationRepository;
    }

    @Transactional(readOnly = true)
    public RouteContext load(UUID alertId, UUID citizenId) {
        AlertEntity alert = alertRepository.findWithAssignedUnitById(alertId)
                .orElseThrow(() -> new NotFoundException("Alerta no encontrada"));
        if (!alert.getCitizen().getId().equals(citizenId)) {
            throw new ForbiddenException("No puedes consultar la ruta de una alerta ajena");
        }
        if (alert.getStatus() != AlertStatus.EN_ROUTE || alert.getAssignedUnit() == null) {
            throw new ConflictException("ROUTE_NOT_AVAILABLE", "La ruta solo está disponible mientras la unidad va en camino");
        }
        UnitLocationEntity location = locationRepository.findById(alert.getAssignedUnit().getId())
                .orElseThrow(() -> new ConflictException("UNIT_LOCATION_UNAVAILABLE", "La unidad aún no reporta ubicación"));
        return new RouteContext(alertId,
                new GeoPoint(location.getLatitude(), location.getLongitude()),
                new GeoPoint(alert.getLatitude(), alert.getLongitude()));
    }
}

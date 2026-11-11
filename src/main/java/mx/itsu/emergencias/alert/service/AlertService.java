package mx.itsu.emergencias.alert.service;

import mx.itsu.emergencias.alert.api.AlertResponse;
import mx.itsu.emergencias.alert.api.CreateAlertRequest;
import mx.itsu.emergencias.alert.domain.AlertEntity;
import mx.itsu.emergencias.alert.domain.AlertStatus;
import mx.itsu.emergencias.alert.domain.AlertType;
import mx.itsu.emergencias.alert.repo.AlertEventRepository;
import mx.itsu.emergencias.alert.repo.AlertRepository;
import mx.itsu.emergencias.common.exception.ConflictException;
import mx.itsu.emergencias.common.exception.ForbiddenException;
import mx.itsu.emergencias.common.exception.NotFoundException;
import mx.itsu.emergencias.dispatch.domain.DispatchStatus;
import mx.itsu.emergencias.dispatch.repo.AlertDispatchRepository;
import mx.itsu.emergencias.dispatch.service.AlertDispatchService;
import mx.itsu.emergencias.unit.domain.UnitEntity;
import mx.itsu.emergencias.unit.repo.UnitRepository;
import mx.itsu.emergencias.user.domain.Role;
import mx.itsu.emergencias.user.domain.UserEntity;
import mx.itsu.emergencias.user.repo.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AlertService {

    private static final List<AlertStatus> ACTIVE_STATUSES = List.of(
            AlertStatus.PENDING, AlertStatus.EN_ROUTE, AlertStatus.ON_SCENE);

    private final AlertRepository alertRepository;
    private final UserRepository userRepository;
    private final UnitRepository unitRepository;
    private final AlertDispatchRepository dispatchRepository;
    private final AlertEventRepository eventRepository;
    private final AlertDomainEventPublisher domainEventPublisher;
    private final AlertResponseMapper responseMapper;
    private final AlertDispatchService dispatchService;

    public AlertService(AlertRepository alertRepository,
                        UserRepository userRepository,
                        UnitRepository unitRepository,
                        AlertDispatchRepository dispatchRepository,
                        AlertEventRepository eventRepository,
                        AlertDomainEventPublisher domainEventPublisher,
                        AlertResponseMapper responseMapper,
                        AlertDispatchService dispatchService) {
        this.alertRepository = alertRepository;
        this.userRepository = userRepository;
        this.unitRepository = unitRepository;
        this.dispatchRepository = dispatchRepository;
        this.eventRepository = eventRepository;
        this.domainEventPublisher = domainEventPublisher;
        this.responseMapper = responseMapper;
        this.dispatchService = dispatchService;
    }

    @Transactional
    public AlertResponse create(UUID citizenId, CreateAlertRequest request) {
        UserEntity citizen = userRepository.findById(citizenId)
                .orElseThrow(() -> new NotFoundException("Ciudadano no encontrado"));
        if (citizen.getRole() != Role.CITIZEN) {
            throw new ForbiddenException("Solo un ciudadano puede crear alertas");
        }
        if (alertRepository.existsByCitizenIdAndStatusIn(citizenId, ACTIVE_STATUSES)) {
            throw new ConflictException("ACTIVE_ALERT_EXISTS", "El ciudadano ya tiene una alerta activa");
        }

        AlertEntity alert = new AlertEntity();
        alert.setCitizen(citizen);
        alert.setType(request.type());
        alert.setRequiredUnitType(request.type().requiredUnitType());
        alert.setDescription(request.description() == null || request.description().isBlank()
                ? null : request.description().trim());
        alert.setLatitude(request.latitude());
        alert.setLongitude(request.longitude());
        alert.setStatus(AlertStatus.PENDING);
        alert = alertRepository.saveAndFlush(alert);

        eventRepository.append(alert.getId(), "ALERT_CREATED", citizenId,
                Map.of("type", alert.getType().name(), "requiredUnitType", alert.getRequiredUnitType().name()));
        domainEventPublisher.publish(alert.getId(), "ALERT_CREATED", citizenId,
                Map.of("type", alert.getType().name(), "requiredUnitType", alert.getRequiredUnitType().name()));

        UUID alertId = alert.getId();
        dispatchService.dispatchInitialRounds(alertId, citizenId);
        AlertEntity created = alertRepository.findWithAssignedUnitById(alertId)
                .orElseThrow(() -> new NotFoundException("Alerta no encontrada"));
        return responseMapper.toResponse(created);
    }

    @Transactional(readOnly = true)
    public AlertResponse activeForCitizen(UUID citizenId) {
        AlertEntity alert = alertRepository.findFirstByCitizenIdAndStatusInOrderByCreatedAtDesc(
                        citizenId, ACTIVE_STATUSES)
                .orElseThrow(() -> new NotFoundException("El ciudadano no tiene una alerta activa"));
        AlertEntity detailed = alertRepository.findWithAssignedUnitById(alert.getId()).orElse(alert);
        return responseMapper.toResponse(detailed);
    }

    @Transactional
    public AlertResponse cancel(UUID alertId, UUID citizenId) {
        AlertEntity alert = alertRepository.findForUpdateById(alertId)
                .orElseThrow(() -> new NotFoundException("Alerta no encontrada"));
        if (!alert.getCitizen().getId().equals(citizenId)) {
            throw new ForbiddenException("No puedes cancelar una alerta de otro ciudadano");
        }
        if (!alert.getStatus().canBeCancelledByCitizen()) {
            throw invalidTransition(alert.getStatus(), "CANCELLED");
        }

        List<AlertDispatchEntityRef> offered = dispatchRepository.findByAlert_IdAndStatus(alertId, DispatchStatus.OFFERED)
                .stream().map(dispatch -> new AlertDispatchEntityRef(dispatch.getId())).toList();
        int cancelledOffers = dispatchRepository.cancelAllOffers(alertId);
        alert.setStatus(AlertStatus.CANCELLED);
        alert.setCancelledAt(Instant.now());
        if (alert.getAssignedUnit() != null) {
            unitRepository.releaseIfBusy(alert.getAssignedUnit().getId());
        }
        alertRepository.save(alert);

        for (AlertDispatchEntityRef dispatch : offered) {
            eventRepository.append(alertId, "DISPATCH_CANCELLED", citizenId,
                    Map.of("dispatchId", dispatch.id().toString(), "reason", "ALERT_CANCELLED"));
        }
        eventRepository.append(alertId, "ALERT_CANCELLED", citizenId,
                Map.of("cancelledOffers", cancelledOffers));
        domainEventPublisher.publish(alertId, "ALERT_CANCELLED", citizenId,
                Map.of("cancelledOffers", cancelledOffers));
        return responseMapper.toResponse(alert);
    }

    @Transactional
    public AlertResponse arrive(UUID alertId, UUID userId) {
        AlertEntity alert = alertRepository.findForUpdateById(alertId)
                .orElseThrow(() -> new NotFoundException("Alerta no encontrada"));
        requireAssignedUnit(alert, userId);
        if (!alert.getStatus().canArrive()) {
            throw invalidTransition(alert.getStatus(), "ON_SCENE");
        }
        alert.setStatus(AlertStatus.ON_SCENE);
        alert.setArrivedAt(Instant.now());
        alertRepository.save(alert);
        eventRepository.append(alertId, "ALERT_ARRIVED", userId, Map.of());
        domainEventPublisher.publish(alertId, "ALERT_ARRIVED", userId, Map.of());
        return responseMapper.toResponse(alert);
    }

    @Transactional
    public AlertResponse resolve(UUID alertId, UUID userId) {
        AlertEntity alert = alertRepository.findForUpdateById(alertId)
                .orElseThrow(() -> new NotFoundException("Alerta no encontrada"));
        requireAssignedUnit(alert, userId);
        if (!alert.getStatus().canResolve()) {
            throw invalidTransition(alert.getStatus(), "RESOLVED");
        }
        alert.setStatus(AlertStatus.RESOLVED);
        alert.setResolvedAt(Instant.now());
        UUID assignedUnitId = alert.getAssignedUnit().getId();
        alertRepository.save(alert);
        unitRepository.releaseIfBusy(assignedUnitId);
        eventRepository.append(alertId, "ALERT_RESOLVED", userId, Map.of());
        domainEventPublisher.publish(alertId, "ALERT_RESOLVED", userId, Map.of());
        return responseMapper.toResponse(alert);
    }

    private void requireAssignedUnit(AlertEntity alert, UUID userId) {
        UnitEntity unit = unitRepository.findByUserId(userId)
                .orElseThrow(() -> new NotFoundException("No existe una unidad para el usuario autenticado"));
        if (alert.getAssignedUnit() == null || !alert.getAssignedUnit().getId().equals(unit.getId())) {
            throw new ForbiddenException("La alerta no está asignada a esta unidad");
        }
    }

    private ConflictException invalidTransition(AlertStatus current, String target) {
        return new ConflictException("INVALID_ALERT_TRANSITION",
                "No se puede cambiar el estado de " + current + " a " + target);
    }

    private record AlertDispatchEntityRef(UUID id) {
    }
}

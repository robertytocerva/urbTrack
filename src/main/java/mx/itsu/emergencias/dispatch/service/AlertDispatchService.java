package mx.itsu.emergencias.dispatch.service;

import mx.itsu.emergencias.alert.api.AlertResponse;
import mx.itsu.emergencias.alert.domain.AlertEntity;
import mx.itsu.emergencias.alert.domain.AlertStatus;
import mx.itsu.emergencias.alert.repo.AlertEventRepository;
import mx.itsu.emergencias.alert.repo.AlertRepository;
import mx.itsu.emergencias.alert.service.AlertDomainEventPublisher;
import mx.itsu.emergencias.alert.service.AlertResponseMapper;
import mx.itsu.emergencias.common.exception.ConflictException;
import mx.itsu.emergencias.common.exception.NotFoundException;
import mx.itsu.emergencias.dispatch.api.DispatchActionResponse;
import mx.itsu.emergencias.dispatch.api.PendingDispatchResponse;
import mx.itsu.emergencias.dispatch.config.DispatchProperties;
import mx.itsu.emergencias.dispatch.domain.AlertDispatchEntity;
import mx.itsu.emergencias.dispatch.domain.DispatchStatus;
import mx.itsu.emergencias.dispatch.repo.AlertDispatchRepository;
import mx.itsu.emergencias.dispatch.repo.DispatchCandidate;
import mx.itsu.emergencias.dispatch.repo.DispatchCandidateRepository;
import mx.itsu.emergencias.unit.domain.UnitEntity;
import mx.itsu.emergencias.unit.repo.UnitRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AlertDispatchService {

    private final AlertRepository alertRepository;
    private final AlertDispatchRepository dispatchRepository;
    private final DispatchCandidateRepository candidateRepository;
    private final UnitRepository unitRepository;
    private final AlertEventRepository eventRepository;
    private final AlertDomainEventPublisher domainEventPublisher;
    private final AlertResponseMapper responseMapper;
    private final DispatchProperties properties;

    public AlertDispatchService(AlertRepository alertRepository,
                                AlertDispatchRepository dispatchRepository,
                                DispatchCandidateRepository candidateRepository,
                                UnitRepository unitRepository,
                                AlertEventRepository eventRepository,
                                AlertDomainEventPublisher domainEventPublisher,
                                AlertResponseMapper responseMapper,
                                DispatchProperties properties) {
        this.alertRepository = alertRepository;
        this.dispatchRepository = dispatchRepository;
        this.candidateRepository = candidateRepository;
        this.unitRepository = unitRepository;
        this.eventRepository = eventRepository;
        this.domainEventPublisher = domainEventPublisher;
        this.responseMapper = responseMapper;
        this.properties = properties;
    }

    @Transactional
    public void dispatchInitialRounds(UUID alertId, UUID actorUserId) {
        AlertEntity alert = alertRepository.findForUpdateById(alertId)
                .orElseThrow(() -> new NotFoundException("Alerta no encontrada"));
        advanceRounds(alert, actorUserId);
    }

    @Transactional(readOnly = true)
    public List<PendingDispatchResponse> pendingForUnit(UUID userId) {
        UnitEntity unit = unitRepository.findByUserId(userId)
                .orElseThrow(() -> new NotFoundException("No existe una unidad para el usuario autenticado"));
        Instant now = Instant.now();
        return dispatchRepository.findByUnit_IdAndStatusAndExpiresAtAfterOrderByOfferedAtAsc(
                        unit.getId(), DispatchStatus.OFFERED, now)
                .stream()
                .map(dispatch -> new PendingDispatchResponse(
                        dispatch.getId(), dispatch.getAlert().getId(), dispatch.getAlert().getType(),
                        dispatch.getAlert().getRequiredUnitType(), dispatch.getAlert().getDescription(),
                        dispatch.getAlert().getLatitude(), dispatch.getAlert().getLongitude(), dispatch.getRound(),
                        dispatch.getDistanceMeters(), dispatch.getExpiresAt()))
                .toList();
    }

    @Transactional
    public AlertResponse accept(UUID dispatchId, UUID userId) {
        UnitEntity unit = unitRepository.findByUserId(userId)
                .orElseThrow(() -> new NotFoundException("No existe una unidad para el usuario autenticado"));
        AlertDispatchEntity dispatch = dispatchRepository.findByIdAndUnit_Id(dispatchId, unit.getId())
                .orElseThrow(() -> new NotFoundException("Oferta de despacho no encontrada"));
        UUID alertId = dispatch.getAlert().getId();
        Instant now = Instant.now();
        if (dispatch.getStatus() != DispatchStatus.OFFERED || !dispatch.getExpiresAt().isAfter(now)) {
            throw new ConflictException("DISPATCH_NOT_OFFERED", "La oferta ya no está vigente");
        }
        if (unitRepository.markBusyIfAvailable(unit.getId()) != 1) {
            throw new ConflictException("UNIT_NOT_AVAILABLE", "La unidad ya no está disponible");
        }
        if (alertRepository.claimPendingAlert(alertId, unit.getId()) != 1) {
            throw new ConflictException("ALERT_ALREADY_ASSIGNED", "La alerta ya fue asignada o dejó de estar pendiente");
        }
        if (dispatchRepository.acceptIfStillOffered(dispatchId, unit.getId()) != 1) {
            throw new ConflictException("DISPATCH_NOT_OFFERED", "La oferta ya no está vigente");
        }

        List<AlertDispatchEntity> otherOffers = dispatchRepository
                .findByAlert_IdAndStatus(alertId, DispatchStatus.OFFERED);
        int cancelled = dispatchRepository.cancelOtherOffers(alertId, dispatchId);
        for (AlertDispatchEntity other : otherOffers) {
            if (!other.getId().equals(dispatchId)) {
                Map<String, Object> cancellation = Map.of(
                        "dispatchId", other.getId().toString(), "reason", "ALERT_ACCEPTED");
                eventRepository.append(alertId, "DISPATCH_CANCELLED", userId, cancellation);
                domainEventPublisher.publish(alertId, "DISPATCH_CANCELLED", userId, cancellation);
            }
        }
        eventRepository.append(alertId, "ALERT_ACCEPTED", userId, Map.of(
                "dispatchId", dispatchId.toString(), "unitId", unit.getId().toString(),
                "cancelledOffers", cancelled));
        domainEventPublisher.publish(alertId, "ALERT_ACCEPTED", userId,
                Map.of("dispatchId", dispatchId.toString(), "unitId", unit.getId().toString()));

        AlertEntity alert = alertRepository.findWithAssignedUnitById(alertId)
                .orElseThrow(() -> new NotFoundException("Alerta no encontrada"));
        return responseMapper.toResponse(alert);
    }

    @Transactional
    public DispatchActionResponse reject(UUID dispatchId, UUID userId) {
        UnitEntity unit = unitRepository.findByUserId(userId)
                .orElseThrow(() -> new NotFoundException("No existe una unidad para el usuario autenticado"));
        AlertDispatchEntity dispatch = dispatchRepository.findByIdAndUnit_Id(dispatchId, unit.getId())
                .orElseThrow(() -> new NotFoundException("Oferta de despacho no encontrada"));
        UUID alertId = dispatch.getAlert().getId();
        AlertEntity alert = alertRepository.findForUpdateById(alertId)
                .orElseThrow(() -> new NotFoundException("Alerta no encontrada"));
        if (alert.getStatus() != AlertStatus.PENDING) {
            throw new ConflictException("ALERT_ALREADY_ASSIGNED", "La alerta ya fue asignada o dejó de estar pendiente");
        }
        if (dispatchRepository.rejectIfStillOffered(dispatchId, unit.getId()) != 1) {
            throw new ConflictException("DISPATCH_NOT_OFFERED", "La oferta ya no está vigente");
        }
        eventRepository.append(alertId, "DISPATCH_REJECTED", userId,
                Map.of("dispatchId", dispatchId.toString(), "unitId", unit.getId().toString()));
        domainEventPublisher.publish(alertId, "DISPATCH_REJECTED", userId,
                Map.of("dispatchId", dispatchId.toString(), "unitId", unit.getId().toString()));
        advanceIfReady(alertId, userId);
        return new DispatchActionResponse(dispatchId, alertId, DispatchStatus.REJECTED);
    }

    @Transactional
    public UUID expireIfDue(UUID dispatchId) {
        AlertDispatchEntity dispatch = dispatchRepository.findById(dispatchId).orElse(null);
        if (dispatch == null) return null;
        UUID alertId = dispatch.getAlert().getId();
        if (dispatchRepository.expireIfDue(dispatchId) != 1) return null;
        eventRepository.append(alertId, "DISPATCH_EXPIRED", null, Map.of("dispatchId", dispatchId.toString()));
        domainEventPublisher.publish(alertId, "DISPATCH_EXPIRED", null,
                Map.of("dispatchId", dispatchId.toString()));
        return alertId;
    }

    @Transactional
    public void advanceIfReady(UUID alertId, UUID actorUserId) {
        AlertEntity alert = alertRepository.findForUpdateById(alertId).orElse(null);
        if (alert == null || alert.getStatus() != AlertStatus.PENDING) return;
        if (dispatchRepository.countByAlert_IdAndStatusAndExpiresAtAfter(
                alertId, DispatchStatus.OFFERED, Instant.now()) > 0) return;
        advanceRounds(alert, actorUserId);
    }

    private void advanceRounds(AlertEntity alert, UUID actorUserId) {
        if (alert.getStatus() != AlertStatus.PENDING) return;
        UUID alertId = alert.getId();
        List<Integer> radii = properties.radiusKmRounds();
        while (alert.getDispatchRound() < radii.size()) {
            short round = (short) (alert.getDispatchRound() + 1);
            int radiusKm = radii.get(round - 1);
            alert.setDispatchRound(round);
            alertRepository.saveAndFlush(alert);
            eventRepository.append(alertId, "DISPATCH_ROUND_STARTED", actorUserId,
                    Map.of("round", (int) round, "radiusKm", radiusKm));
            domainEventPublisher.publish(alertId, "DISPATCH_ROUND_STARTED", actorUserId,
                    Map.of("round", (int) round, "radiusKm", radiusKm));

            List<DispatchCandidate> candidates = candidateRepository.findCandidates(
                    alertId, alert.getLatitude(), alert.getLongitude(), alert.getRequiredUnitType(),
                    radiusKm * 1000.0, properties.locationMaxAgeSeconds(), properties.offersPerRound());
            if (candidates.isEmpty()) continue;

            Instant expiresAt = Instant.now().plusSeconds(properties.offerTtlSeconds());
            List<AlertDispatchEntity> offers = new ArrayList<>(candidates.size());
            for (DispatchCandidate candidate : candidates) {
                AlertDispatchEntity offer = new AlertDispatchEntity();
                offer.setAlert(alert);
                offer.setUnit(unitRepository.getReferenceById(candidate.unitId()));
                offer.setRound(round);
                offer.setDistanceMeters(candidate.distanceMeters());
                offer.setExpiresAt(expiresAt);
                offers.add(offer);
            }
            dispatchRepository.saveAllAndFlush(offers);
            for (AlertDispatchEntity offer : offers) {
                Map<String, Object> payload = Map.of(
                        "dispatchId", offer.getId().toString(),
                        "unitId", offer.getUnit().getId().toString(),
                        "round", (int) round,
                        "distanceMeters", offer.getDistanceMeters(),
                        "expiresAt", expiresAt.toString());
                eventRepository.append(alertId, "DISPATCH_OFFERED", actorUserId, payload);
                domainEventPublisher.publish(alertId, "DISPATCH_OFFERED", actorUserId, payload);
            }
            return;
        }

        alert.setStatus(AlertStatus.EXPIRED);
        alertRepository.save(alert);
        eventRepository.append(alertId, "ALERT_EXPIRED", actorUserId,
                Map.of("reason", "NO_AVAILABLE_UNITS", "rounds", radii.size()));
        domainEventPublisher.publish(alertId, "ALERT_EXPIRED", actorUserId,
                Map.of("reason", "NO_AVAILABLE_UNITS", "rounds", radii.size()));
    }
}

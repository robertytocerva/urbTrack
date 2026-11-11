package mx.itsu.emergencias.tracking.service;

import mx.itsu.emergencias.common.exception.InvalidRequestException;
import mx.itsu.emergencias.common.exception.NotFoundException;
import mx.itsu.emergencias.alert.domain.AlertEntity;
import mx.itsu.emergencias.alert.domain.AlertStatus;
import mx.itsu.emergencias.alert.repo.AlertRepository;
import mx.itsu.emergencias.realtime.event.UnitLocationTrackingEvent;
import mx.itsu.emergencias.tracking.api.BatchUnitLocationResponse;
import mx.itsu.emergencias.tracking.api.UnitLocationRequest;
import mx.itsu.emergencias.tracking.api.UnitLocationResult;
import mx.itsu.emergencias.tracking.repo.UnitLocationHistoryRepository;
import mx.itsu.emergencias.unit.domain.UnitEntity;
import mx.itsu.emergencias.unit.repo.UnitLocationRepository;
import mx.itsu.emergencias.unit.repo.UnitRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class UnitLocationService {

    private final UnitRepository unitRepository;
    private final UnitLocationHistoryRepository historyRepository;
    private final UnitLocationRepository locationRepository;
    private final AlertRepository alertRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final long maxFutureSeconds;

    public UnitLocationService(UnitRepository unitRepository,
                               UnitLocationHistoryRepository historyRepository,
                               UnitLocationRepository locationRepository,
                               AlertRepository alertRepository,
                               ApplicationEventPublisher eventPublisher,
                               @Value("${app.location.max-future-seconds:60}") long maxFutureSeconds) {
        this.unitRepository = unitRepository;
        this.historyRepository = historyRepository;
        this.locationRepository = locationRepository;
        this.alertRepository = alertRepository;
        this.eventPublisher = eventPublisher;
        this.maxFutureSeconds = maxFutureSeconds;
    }

    @Transactional
    public UnitLocationResult record(UUID userId, UnitLocationRequest request) {
        UnitEntity unit = findUnit(userId);
        validateRecordedAt(request);
        return recordForUnit(unit.getId(), request);
    }

    @Transactional
    public BatchUnitLocationResponse recordBatch(UUID userId, List<UnitLocationRequest> requests) {
        UnitEntity unit = findUnit(userId);
        if (requests == null) {
            throw new InvalidRequestException("El cuerpo debe ser un arreglo de ubicaciones");
        }
        for (UnitLocationRequest request : requests) {
            if (request == null) {
                throw new InvalidRequestException("El lote no puede contener ubicaciones nulas");
            }
            validateRecordedAt(request);
        }

        List<UnitLocationResult> results = new ArrayList<>(requests.size());
        int stored = 0;
        int duplicates = 0;
        int updated = 0;
        for (UnitLocationRequest request : requests) {
            UnitLocationResult result = recordForUnit(unit.getId(), request);
            results.add(result);
            if (result.duplicate()) {
                duplicates++;
            } else {
                stored++;
            }
            if (result.currentLocationUpdated()) {
                updated++;
            }
        }
        return new BatchUnitLocationResponse(requests.size(), stored, duplicates, updated, List.copyOf(results));
    }

    private UnitEntity findUnit(UUID userId) {
        return unitRepository.findByUserId(userId)
                .orElseThrow(() -> new NotFoundException("No existe una unidad para el usuario autenticado"));
    }

    private void validateRecordedAt(UnitLocationRequest request) {
        if (request.recordedAt() == null) {
            throw new InvalidRequestException("recordedAt es obligatorio");
        }
        if (request.recordedAt().isAfter(Instant.now().plusSeconds(maxFutureSeconds))) {
            throw new InvalidRequestException("recordedAt no puede estar más de un minuto en el futuro");
        }
    }

    private UnitLocationResult recordForUnit(UUID unitId, UnitLocationRequest request) {
        AlertEntity activeAlert = alertRepository.findFirstByAssignedUnit_IdAndStatusIn(unitId,
                List.of(AlertStatus.EN_ROUTE, AlertStatus.ON_SCENE)).orElse(null);
        UUID alertId = activeAlert == null ? null : activeAlert.getId();
        int inserted = historyRepository.insertIfEventIsNew(
                request.eventId(), unitId, alertId, request.latitude(), request.longitude(), request.heading(),
                request.speedKmh(), request.recordedAt());
        if (inserted == 0) {
            return new UnitLocationResult(request.eventId(), true, false);
        }

        int updated = locationRepository.upsertIfNotOlder(
                unitId, request.latitude(), request.longitude(), request.heading(), request.speedKmh(),
                request.recordedAt());
        if (updated == 1 && activeAlert != null && activeAlert.getStatus() == AlertStatus.EN_ROUTE) {
            eventPublisher.publishEvent(new UnitLocationTrackingEvent(
                    activeAlert.getId(), unitId, request.latitude(), request.longitude(), request.heading(),
                    request.speedKmh(), request.recordedAt()));
        }
        return new UnitLocationResult(request.eventId(), false, updated == 1);
    }
}

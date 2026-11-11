package mx.itsu.emergencias.realtime.service;

import mx.itsu.emergencias.alert.api.AlertResponse;
import mx.itsu.emergencias.alert.domain.AlertEntity;
import mx.itsu.emergencias.alert.event.AlertDomainEvent;
import mx.itsu.emergencias.alert.repo.AlertRepository;
import mx.itsu.emergencias.alert.service.AlertResponseMapper;
import mx.itsu.emergencias.dispatch.api.PendingDispatchResponse;
import mx.itsu.emergencias.dispatch.domain.AlertDispatchEntity;
import mx.itsu.emergencias.dispatch.repo.AlertDispatchRepository;
import mx.itsu.emergencias.realtime.api.AlertStatusNotification;
import mx.itsu.emergencias.realtime.api.DispatchUpdateNotification;
import mx.itsu.emergencias.realtime.api.UnitLocationUpdate;
import mx.itsu.emergencias.realtime.event.UnitLocationTrackingEvent;
import mx.itsu.emergencias.unit.domain.UnitEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.UUID;

@Component
public class RealtimeEventListener {

    private final SimpMessagingTemplate messagingTemplate;
    private final AlertRepository alertRepository;
    private final AlertDispatchRepository dispatchRepository;
    private final AlertResponseMapper alertResponseMapper;

    public RealtimeEventListener(SimpMessagingTemplate messagingTemplate,
                                 AlertRepository alertRepository,
                                 AlertDispatchRepository dispatchRepository,
                                 AlertResponseMapper alertResponseMapper) {
        this.messagingTemplate = messagingTemplate;
        this.alertRepository = alertRepository;
        this.dispatchRepository = dispatchRepository;
        this.alertResponseMapper = alertResponseMapper;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAlertDomainEvent(AlertDomainEvent event) {
        switch (event.eventType()) {
            case "DISPATCH_OFFERED" -> publishDispatchOffer(event);
            case "DISPATCH_CANCELLED", "DISPATCH_EXPIRED" -> publishDispatchUpdate(event);
            case "ALERT_CREATED", "ALERT_ACCEPTED", "ALERT_ARRIVED", "ALERT_RESOLVED",
                    "ALERT_CANCELLED", "ALERT_EXPIRED" -> publishAlertState(event);
            default -> { }
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onUnitLocation(UnitLocationTrackingEvent event) {
        UnitLocationUpdate update = new UnitLocationUpdate(
                event.alertId(), event.unitId(), event.latitude(), event.longitude(), event.heading(),
                event.speedKmh(), event.recordedAt());
        messagingTemplate.convertAndSend("/topic/alerts/" + event.alertId(), update);
    }

    private void publishAlertState(AlertDomainEvent event) {
        AlertEntity alert = alertRepository.findWithAssignedUnitById(event.alertId()).orElse(null);
        if (alert == null) return;
        AlertResponse response = alertResponseMapper.toResponse(alert);
        AlertStatusNotification notification = new AlertStatusNotification(
                event.alertId(), event.eventType(), response, event.occurredAt());
        messagingTemplate.convertAndSendToUser(alert.getCitizen().getId().toString(),
                "/queue/alerts", notification);
        messagingTemplate.convertAndSend("/topic/alerts/" + event.alertId(), notification);
    }

    private void publishDispatchOffer(AlertDomainEvent event) {
        UUID dispatchId = UUID.fromString((String) event.payload().get("dispatchId"));
        AlertDispatchEntity dispatch = dispatchRepository.findWithAlertAndUnitUserById(dispatchId).orElse(null);
        if (dispatch == null) return;
        UnitEntity unit = dispatch.getUnit();
        PendingDispatchResponse offer = new PendingDispatchResponse(
                dispatch.getId(), dispatch.getAlert().getId(), dispatch.getAlert().getType(),
                dispatch.getAlert().getRequiredUnitType(), dispatch.getAlert().getDescription(),
                dispatch.getAlert().getLatitude(), dispatch.getAlert().getLongitude(), dispatch.getRound(),
                dispatch.getDistanceMeters(), dispatch.getExpiresAt());
        messagingTemplate.convertAndSendToUser(unit.getUser().getId().toString(), "/queue/dispatches", offer);
    }

    private void publishDispatchUpdate(AlertDomainEvent event) {
        UUID dispatchId = UUID.fromString((String) event.payload().get("dispatchId"));
        AlertDispatchEntity dispatch = dispatchRepository.findWithAlertAndUnitUserById(dispatchId).orElse(null);
        if (dispatch == null) return;
        String reason = (String) event.payload().get("reason");
        DispatchUpdateNotification notification = new DispatchUpdateNotification(
                dispatch.getId(), dispatch.getAlert().getId(), dispatch.getStatus(), reason);
        messagingTemplate.convertAndSendToUser(dispatch.getUnit().getUser().getId().toString(),
                "/queue/dispatches", notification);
    }
}

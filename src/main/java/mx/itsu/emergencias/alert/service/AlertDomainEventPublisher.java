package mx.itsu.emergencias.alert.service;

import mx.itsu.emergencias.alert.event.AlertDomainEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Component
public class AlertDomainEventPublisher {

    private final ApplicationEventPublisher publisher;

    public AlertDomainEventPublisher(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    public void publish(UUID alertId, String eventType, UUID actorUserId, Map<String, Object> payload) {
        publisher.publishEvent(new AlertDomainEvent(alertId, eventType, actorUserId, Map.copyOf(payload), Instant.now()));
    }
}

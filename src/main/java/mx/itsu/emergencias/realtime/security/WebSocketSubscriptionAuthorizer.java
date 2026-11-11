package mx.itsu.emergencias.realtime.security;

import mx.itsu.emergencias.alert.domain.AlertEntity;
import mx.itsu.emergencias.alert.repo.AlertRepository;
import mx.itsu.emergencias.common.exception.ForbiddenException;
import mx.itsu.emergencias.unit.domain.UnitEntity;
import mx.itsu.emergencias.unit.repo.UnitRepository;
import mx.itsu.emergencias.user.domain.Role;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.Principal;
import java.util.UUID;

@Service
public class WebSocketSubscriptionAuthorizer {

    private final AlertRepository alertRepository;
    private final UnitRepository unitRepository;

    public WebSocketSubscriptionAuthorizer(AlertRepository alertRepository, UnitRepository unitRepository) {
        this.alertRepository = alertRepository;
        this.unitRepository = unitRepository;
    }

    @Transactional(readOnly = true)
    public void authorize(Principal principal, Role role, String destination) {
        UUID userId = UUID.fromString(principal.getName());
        if ("/user/queue/alerts".equals(destination)) {
            if (role != Role.CITIZEN) throw new ForbiddenException("Solo ciudadanos pueden recibir sus alertas");
            return;
        }
        if ("/user/queue/dispatches".equals(destination)) {
            if (role != Role.UNIT) throw new ForbiddenException("Solo unidades pueden recibir ofertas de despacho");
            return;
        }
        if (destination != null && destination.startsWith("/topic/alerts/")) {
            UUID alertId;
            try {
                alertId = UUID.fromString(destination.substring("/topic/alerts/".length()));
            } catch (IllegalArgumentException exception) {
                throw new ForbiddenException("Destino de alerta inválido");
            }
            AlertEntity alert = alertRepository.findById(alertId)
                    .orElseThrow(() -> new ForbiddenException("No puedes suscribirte a esta alerta"));
            if (role == Role.CITIZEN && alert.getCitizen().getId().equals(userId)) return;
            if (role == Role.UNIT) {
                UnitEntity unit = unitRepository.findByUserId(userId).orElse(null);
                if (unit != null && alert.getAssignedUnit() != null
                        && alert.getAssignedUnit().getId().equals(unit.getId())) return;
            }
            throw new ForbiddenException("No puedes suscribirte a esta alerta");
        }
        throw new ForbiddenException("Destino de suscripción no permitido");
    }
}

package mx.itsu.emergencias.alert.api;

import jakarta.validation.Valid;
import mx.itsu.emergencias.alert.service.AlertService;
import mx.itsu.emergencias.routing.api.RouteResponse;
import mx.itsu.emergencias.routing.service.AlertRoutingService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/alerts")
public class AlertController {

    private final AlertService alertService;
    private final AlertRoutingService routingService;

    public AlertController(AlertService alertService, AlertRoutingService routingService) {
        this.alertService = alertService;
        this.routingService = routingService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AlertResponse create(@AuthenticationPrincipal Jwt jwt,
                                @Valid @RequestBody CreateAlertRequest request) {
        return alertService.create(userId(jwt), request);
    }

    @GetMapping("/active")
    public AlertResponse active(@AuthenticationPrincipal Jwt jwt) {
        return alertService.activeForCitizen(userId(jwt));
    }

    @PostMapping("/{alertId}/cancel")
    public AlertResponse cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID alertId) {
        return alertService.cancel(alertId, userId(jwt));
    }

    @PostMapping("/{alertId}/arrive")
    public AlertResponse arrive(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID alertId) {
        return alertService.arrive(alertId, userId(jwt));
    }

    @PostMapping("/{alertId}/resolve")
    public AlertResponse resolve(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID alertId) {
        return alertService.resolve(alertId, userId(jwt));
    }

    @GetMapping("/{alertId}/route")
    public RouteResponse route(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID alertId) {
        return routingService.route(alertId, userId(jwt));
    }

    private UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}

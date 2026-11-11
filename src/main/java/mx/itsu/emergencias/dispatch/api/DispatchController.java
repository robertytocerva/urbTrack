package mx.itsu.emergencias.dispatch.api;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import mx.itsu.emergencias.alert.api.AlertResponse;
import mx.itsu.emergencias.dispatch.service.AlertDispatchService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/dispatches")
@Tag(name = "Despacho", description = "Ofertas, aceptación y rechazo de despachos")
@SecurityRequirement(name = "bearerAuth")
public class DispatchController {

    private final AlertDispatchService dispatchService;

    public DispatchController(AlertDispatchService dispatchService) {
        this.dispatchService = dispatchService;
    }

    @GetMapping("/pending")
    public List<PendingDispatchResponse> pending(@AuthenticationPrincipal Jwt jwt) {
        return dispatchService.pendingForUnit(userId(jwt));
    }

    @PostMapping("/{dispatchId}/accept")
    public AlertResponse accept(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID dispatchId) {
        return dispatchService.accept(dispatchId, userId(jwt));
    }

    @PostMapping("/{dispatchId}/reject")
    public DispatchActionResponse reject(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID dispatchId) {
        return dispatchService.reject(dispatchId, userId(jwt));
    }

    private UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}

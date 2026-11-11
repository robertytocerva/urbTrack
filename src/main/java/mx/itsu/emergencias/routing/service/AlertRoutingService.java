package mx.itsu.emergencias.routing.service;

import mx.itsu.emergencias.common.geo.GeoPoint;
import mx.itsu.emergencias.common.geo.Haversine;
import mx.itsu.emergencias.routing.api.RouteResponse;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class AlertRoutingService {

    private final AlertRouteContextService contextService;
    private final OsrmRouteClient osrmRouteClient;

    public AlertRoutingService(AlertRouteContextService contextService, OsrmRouteClient osrmRouteClient) {
        this.contextService = contextService;
        this.osrmRouteClient = osrmRouteClient;
    }

    public RouteResponse route(UUID alertId, UUID citizenId) {
        RouteContext context = contextService.load(alertId, citizenId);
        try {
            var route = osrmRouteClient.findRoute(context.origin(), context.destination());
            if (route.isPresent()) {
                OsrmRoute value = route.get();
                return new RouteResponse(context.alertId(), value.coordinates(), value.distanceMeters(),
                        value.durationSeconds(), false);
            }
        } catch (RuntimeException ignored) {
            // OSRM es opcional; si falla se devuelve la línea recta como fallback.
        }
        return new RouteResponse(context.alertId(), List.of(context.origin(), context.destination()),
                Haversine.distanceMeters(context.origin(), context.destination()), null, true);
    }
}

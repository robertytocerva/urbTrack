package mx.itsu.emergencias.unit.api;

import jakarta.validation.Valid;
import mx.itsu.emergencias.tracking.api.BatchUnitLocationResponse;
import mx.itsu.emergencias.tracking.api.UnitLocationRequest;
import mx.itsu.emergencias.tracking.api.UnitLocationResult;
import mx.itsu.emergencias.tracking.service.UnitLocationService;
import mx.itsu.emergencias.unit.service.NearbyUnitService;
import mx.itsu.emergencias.unit.service.UnitStatusService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/units")
public class UnitController {

    private final UnitStatusService unitStatusService;
    private final UnitLocationService unitLocationService;
    private final NearbyUnitService nearbyUnitService;

    public UnitController(UnitStatusService unitStatusService,
                          UnitLocationService unitLocationService,
                          NearbyUnitService nearbyUnitService) {
        this.unitStatusService = unitStatusService;
        this.unitLocationService = unitLocationService;
        this.nearbyUnitService = nearbyUnitService;
    }

    @PutMapping("/me/status")
    public UnitStatusResponse updateStatus(@AuthenticationPrincipal Jwt jwt,
                                           @Valid @RequestBody UpdateUnitStatusRequest request) {
        return unitStatusService.update(userId(jwt), request.status());
    }

    @PostMapping("/me/location")
    public UnitLocationResult recordLocation(@AuthenticationPrincipal Jwt jwt,
                                              @Valid @RequestBody UnitLocationRequest request) {
        return unitLocationService.record(userId(jwt), request);
    }

    @PostMapping("/me/locations/batch")
    public BatchUnitLocationResponse recordLocationBatch(@AuthenticationPrincipal Jwt jwt,
                                                          @RequestBody List<@Valid UnitLocationRequest> requests) {
        return unitLocationService.recordBatch(userId(jwt), requests);
    }

    @GetMapping("/nearby")
    public List<NearbyUnitResponse> nearby(@RequestParam double lat,
                                           @RequestParam double lon,
                                           @RequestParam double radiusKm) {
        return nearbyUnitService.find(lat, lon, radiusKm);
    }

    private UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}

package mx.itsu.emergencias.unit.service;

import mx.itsu.emergencias.common.exception.InvalidRequestException;
import mx.itsu.emergencias.unit.api.NearbyUnitResponse;
import mx.itsu.emergencias.unit.repo.NearbyUnitRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class NearbyUnitService {

    private final NearbyUnitRepository nearbyUnitRepository;
    private final int locationMaxAgeSeconds;

    public NearbyUnitService(NearbyUnitRepository nearbyUnitRepository,
                             @Value("${app.dispatch.location-max-age-seconds:120}") int locationMaxAgeSeconds) {
        this.nearbyUnitRepository = nearbyUnitRepository;
        this.locationMaxAgeSeconds = locationMaxAgeSeconds;
    }

    @Transactional(readOnly = true)
    public List<NearbyUnitResponse> find(double latitude, double longitude, double radiusKm) {
        if (!Double.isFinite(latitude) || latitude < -90 || latitude > 90) {
            throw new InvalidRequestException("lat debe estar entre -90 y 90");
        }
        if (!Double.isFinite(longitude) || longitude < -180 || longitude > 180) {
            throw new InvalidRequestException("lon debe estar entre -180 y 180");
        }
        if (!Double.isFinite(radiusKm) || radiusKm <= 0 || !Double.isFinite(radiusKm * 1000.0)) {
            throw new InvalidRequestException("radiusKm debe ser un número finito mayor que cero");
        }
        return nearbyUnitRepository.findAvailableWithinRadius(latitude, longitude, radiusKm * 1000.0,
                locationMaxAgeSeconds);
    }
}

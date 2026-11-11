package mx.itsu.emergencias.unit.repo;

import mx.itsu.emergencias.unit.domain.UnitLocationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

public interface UnitLocationRepository extends JpaRepository<UnitLocationEntity, UUID> {

    @Modifying
    @Query(value = """
            INSERT INTO unit_locations (unit_id, latitude, longitude, heading, speed_kmh, recorded_at, updated_at)
            VALUES (:unitId, :latitude, :longitude, :heading, :speedKmh, :recordedAt, now())
            ON CONFLICT (unit_id) DO UPDATE
               SET latitude = EXCLUDED.latitude,
                   longitude = EXCLUDED.longitude,
                   heading = EXCLUDED.heading,
                   speed_kmh = EXCLUDED.speed_kmh,
                   recorded_at = EXCLUDED.recorded_at,
                   updated_at = now()
             WHERE unit_locations.recorded_at <= EXCLUDED.recorded_at
            """, nativeQuery = true)
    int upsertIfNotOlder(@Param("unitId") UUID unitId,
                         @Param("latitude") double latitude,
                         @Param("longitude") double longitude,
                         @Param("heading") Double heading,
                         @Param("speedKmh") Double speedKmh,
                         @Param("recordedAt") Instant recordedAt);
}

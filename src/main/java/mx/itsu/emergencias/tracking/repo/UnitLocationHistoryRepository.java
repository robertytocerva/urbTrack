package mx.itsu.emergencias.tracking.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

public interface UnitLocationHistoryRepository extends JpaRepository<mx.itsu.emergencias.tracking.domain.UnitLocationHistoryEntity, Long> {

    @Modifying
    @Query(value = """
            INSERT INTO unit_location_history
                (event_id, unit_id, alert_id, latitude, longitude, heading, speed_kmh, recorded_at)
            VALUES
                (:eventId, :unitId, :alertId, :latitude, :longitude, :heading, :speedKmh, :recordedAt)
            ON CONFLICT (event_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfEventIsNew(@Param("eventId") UUID eventId,
                           @Param("unitId") UUID unitId,
                           @Param("alertId") UUID alertId,
                           @Param("latitude") double latitude,
                           @Param("longitude") double longitude,
                           @Param("heading") Double heading,
                           @Param("speedKmh") Double speedKmh,
                           @Param("recordedAt") Instant recordedAt);
}

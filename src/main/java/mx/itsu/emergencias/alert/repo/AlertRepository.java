package mx.itsu.emergencias.alert.repo;

import jakarta.persistence.LockModeType;
import mx.itsu.emergencias.alert.domain.AlertEntity;
import mx.itsu.emergencias.alert.domain.AlertStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AlertRepository extends JpaRepository<AlertEntity, UUID> {

    boolean existsByCitizenIdAndStatusIn(UUID citizenId, List<AlertStatus> statuses);

    Optional<AlertEntity> findFirstByCitizenIdAndStatusInOrderByCreatedAtDesc(
            UUID citizenId, List<AlertStatus> statuses);

    @EntityGraph(attributePaths = {"assignedUnit", "citizen"})
    @Query("select a from AlertEntity a where a.id = :id")
    Optional<AlertEntity> findWithAssignedUnitById(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from AlertEntity a where a.id = :id")
    Optional<AlertEntity> findForUpdateById(@Param("id") UUID id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE alerts
               SET status = 'EN_ROUTE',
                   assigned_unit_id = :unitId,
                   accepted_at = now(),
                   version = version + 1
             WHERE id = :alertId AND status = 'PENDING'
            """, nativeQuery = true)
    int claimPendingAlert(@Param("alertId") UUID alertId, @Param("unitId") UUID unitId);

    @Query(value = """
            SELECT a.id
              FROM alerts a
             WHERE a.status = 'PENDING'
               AND NOT EXISTS (
                   SELECT 1 FROM alert_dispatches d
                    WHERE d.alert_id = a.id
                      AND d.status = 'OFFERED'
                      AND d.expires_at > :now
               )
             ORDER BY a.created_at
            """, nativeQuery = true)
    List<UUID> findPendingWithoutLiveOffers(@Param("now") Instant now);
}

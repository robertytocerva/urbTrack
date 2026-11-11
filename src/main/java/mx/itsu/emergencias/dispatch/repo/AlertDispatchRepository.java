package mx.itsu.emergencias.dispatch.repo;

import mx.itsu.emergencias.dispatch.domain.AlertDispatchEntity;
import mx.itsu.emergencias.dispatch.domain.DispatchStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AlertDispatchRepository extends JpaRepository<AlertDispatchEntity, UUID> {

    @EntityGraph(attributePaths = "alert")
    List<AlertDispatchEntity> findByUnit_IdAndStatusAndExpiresAtAfterOrderByOfferedAtAsc(
            UUID unitId, DispatchStatus status, Instant now);

    Optional<AlertDispatchEntity> findByIdAndUnit_Id(UUID dispatchId, UUID unitId);

    List<AlertDispatchEntity> findByAlert_IdAndStatus(UUID alertId, DispatchStatus status);

    long countByAlert_IdAndStatusAndExpiresAtAfter(UUID alertId, DispatchStatus status, Instant now);

    @Modifying
    @Query(value = """
            UPDATE alert_dispatches
               SET status = 'ACCEPTED', responded_at = now()
             WHERE id = :dispatchId
               AND unit_id = :unitId
               AND status = 'OFFERED'
               AND expires_at > now()
            """, nativeQuery = true)
    int acceptIfStillOffered(@Param("dispatchId") UUID dispatchId, @Param("unitId") UUID unitId);

    @Modifying
    @Query(value = """
            UPDATE alert_dispatches
               SET status = 'REJECTED', responded_at = now()
             WHERE id = :dispatchId
               AND unit_id = :unitId
               AND status = 'OFFERED'
               AND expires_at > now()
            """, nativeQuery = true)
    int rejectIfStillOffered(@Param("dispatchId") UUID dispatchId, @Param("unitId") UUID unitId);

    @Modifying
    @Query(value = """
            UPDATE alert_dispatches
               SET status = 'EXPIRED', responded_at = now()
             WHERE id = :dispatchId
               AND status = 'OFFERED'
               AND expires_at <= now()
            """, nativeQuery = true)
    int expireIfDue(@Param("dispatchId") UUID dispatchId);

    @Modifying
    @Query(value = """
            UPDATE alert_dispatches
               SET status = 'CANCELLED', responded_at = now()
             WHERE alert_id = :alertId
               AND id <> :acceptedDispatchId
               AND status = 'OFFERED'
            """, nativeQuery = true)
    int cancelOtherOffers(@Param("alertId") UUID alertId,
                          @Param("acceptedDispatchId") UUID acceptedDispatchId);

    @Modifying
    @Query(value = """
            UPDATE alert_dispatches
               SET status = 'CANCELLED', responded_at = now()
             WHERE alert_id = :alertId AND status = 'OFFERED'
            """, nativeQuery = true)
    int cancelAllOffers(@Param("alertId") UUID alertId);

    @Query("""
            select new mx.itsu.emergencias.dispatch.repo.DispatchExpirationRef(d.id, d.alert.id)
              from AlertDispatchEntity d
             where d.status = mx.itsu.emergencias.dispatch.domain.DispatchStatus.OFFERED
               and d.expiresAt <= :now
            """)
    List<DispatchExpirationRef> findExpiredReferences(@Param("now") Instant now);
}

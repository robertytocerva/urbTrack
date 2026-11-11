package mx.itsu.emergencias.unit.repo;

import mx.itsu.emergencias.unit.domain.UnitEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface UnitRepository extends JpaRepository<UnitEntity, UUID> {
    Optional<UnitEntity> findByUserId(UUID userId);

    @EntityGraph(attributePaths = "user")
    Optional<UnitEntity> findWithUserById(UUID id);

    boolean existsByIdentifier(String identifier);

    @Modifying
    @Query(value = "UPDATE units SET status = 'BUSY', updated_at = now() WHERE id = :unitId AND status = 'AVAILABLE'",
            nativeQuery = true)
    int markBusyIfAvailable(@Param("unitId") UUID unitId);

    @Modifying
    @Query(value = "UPDATE units SET status = 'AVAILABLE', updated_at = now() WHERE id = :unitId AND status = 'BUSY'",
            nativeQuery = true)
    int releaseIfBusy(@Param("unitId") UUID unitId);
}

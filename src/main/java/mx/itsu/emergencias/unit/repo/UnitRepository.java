package mx.itsu.emergencias.unit.repo;

import mx.itsu.emergencias.unit.domain.UnitEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UnitRepository extends JpaRepository<UnitEntity, UUID> {
    Optional<UnitEntity> findByUserId(UUID userId);

    boolean existsByIdentifier(String identifier);
}

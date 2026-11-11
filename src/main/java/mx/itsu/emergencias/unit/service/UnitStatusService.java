package mx.itsu.emergencias.unit.service;

import mx.itsu.emergencias.common.exception.ConflictException;
import mx.itsu.emergencias.common.exception.InvalidRequestException;
import mx.itsu.emergencias.common.exception.NotFoundException;
import mx.itsu.emergencias.unit.api.UnitStatusResponse;
import mx.itsu.emergencias.unit.domain.UnitEntity;
import mx.itsu.emergencias.unit.domain.UnitStatus;
import mx.itsu.emergencias.unit.repo.UnitRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class UnitStatusService {

    private final UnitRepository unitRepository;

    public UnitStatusService(UnitRepository unitRepository) {
        this.unitRepository = unitRepository;
    }

    @Transactional
    public UnitStatusResponse update(UUID userId, UnitStatus requestedStatus) {
        if (requestedStatus == null || requestedStatus == UnitStatus.BUSY) {
            throw new InvalidRequestException("El estado manual debe ser AVAILABLE u OFFLINE");
        }

        UnitEntity unit = unitRepository.findByUserId(userId)
                .orElseThrow(() -> new NotFoundException("No existe una unidad para el usuario autenticado"));
        if (unit.getStatus() == UnitStatus.BUSY) {
            throw new ConflictException("UNIT_BUSY", "Una unidad ocupada no puede cambiar su estado manualmente");
        }

        unit.setStatus(requestedStatus);
        unitRepository.save(unit);
        return new UnitStatusResponse(unit.getId(), unit.getStatus());
    }
}

package mx.itsu.emergencias;

import mx.itsu.emergencias.alert.domain.AlertType;
import mx.itsu.emergencias.unit.domain.UnitType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AlertTypeTest {

    @Test
    void mapsEachAlertTypeToItsRequiredUnitType() {
        assertEquals(UnitType.POLICE, AlertType.ROBBERY.requiredUnitType());
        assertEquals(UnitType.FIRE, AlertType.FIRE.requiredUnitType());
        assertEquals(UnitType.AMBULANCE, AlertType.MEDICAL.requiredUnitType());
        assertEquals(UnitType.AMBULANCE, AlertType.ACCIDENT.requiredUnitType());
        assertEquals(UnitType.CIVIL_PROTECTION, AlertType.OTHER.requiredUnitType());
    }
}

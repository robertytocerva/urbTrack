package mx.itsu.emergencias.alert.domain;

import mx.itsu.emergencias.unit.domain.UnitType;

public enum AlertType {
    ROBBERY(UnitType.POLICE),
    FIRE(UnitType.FIRE),
    MEDICAL(UnitType.AMBULANCE),
    ACCIDENT(UnitType.AMBULANCE),
    OTHER(UnitType.CIVIL_PROTECTION);

    private final UnitType requiredUnitType;

    AlertType(UnitType requiredUnitType) {
        this.requiredUnitType = requiredUnitType;
    }

    public UnitType requiredUnitType() {
        return requiredUnitType;
    }
}

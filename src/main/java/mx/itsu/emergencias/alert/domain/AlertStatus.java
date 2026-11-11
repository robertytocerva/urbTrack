package mx.itsu.emergencias.alert.domain;

import java.util.EnumSet;
import java.util.Set;

public enum AlertStatus {
    PENDING,
    EN_ROUTE,
    ON_SCENE,
    RESOLVED,
    CANCELLED,
    EXPIRED;

    private static final Set<AlertStatus> ACTIVE = EnumSet.of(PENDING, EN_ROUTE, ON_SCENE);

    public boolean isActive() {
        return ACTIVE.contains(this);
    }

    public boolean canBeCancelledByCitizen() {
        return this == PENDING || this == EN_ROUTE;
    }

    public boolean canArrive() {
        return this == EN_ROUTE;
    }

    public boolean canResolve() {
        return this == ON_SCENE;
    }
}

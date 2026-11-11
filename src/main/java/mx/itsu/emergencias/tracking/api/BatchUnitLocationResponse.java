package mx.itsu.emergencias.tracking.api;

import java.util.List;

public record BatchUnitLocationResponse(
        int received,
        int stored,
        int duplicates,
        int currentLocationsUpdated,
        List<UnitLocationResult> results
) {
}

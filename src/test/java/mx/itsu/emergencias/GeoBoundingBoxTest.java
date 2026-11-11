package mx.itsu.emergencias;

import mx.itsu.emergencias.common.geo.GeoBoundingBox;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeoBoundingBoxTest {

    @Test
    void boundingBoxSplitsLongitudeAcrossAntimeridian() {
        GeoBoundingBox box = GeoBoundingBox.around(0.0, 179.9, 50_000);

        assertFalse(box.allLongitudes());
        assertTrue(box.crossesAntimeridian());
        assertTrue(box.minLongitude() > box.maxLongitude());
    }

    @Test
    void boundingBoxIncludesAllLongitudesWhenRadiusReachesPole() {
        GeoBoundingBox box = GeoBoundingBox.around(89.9, 40.0, 50_000);

        assertTrue(box.allLongitudes());
        assertFalse(box.crossesAntimeridian());
        assertTrue(box.maxLatitude() == 90.0);
    }
}

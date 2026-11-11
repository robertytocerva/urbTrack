package mx.itsu.emergencias.unit.repo;

import mx.itsu.emergencias.common.geo.GeoBoundingBox;
import mx.itsu.emergencias.unit.api.NearbyUnitResponse;
import mx.itsu.emergencias.unit.domain.UnitType;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class NearbyUnitRepository {

    private static final String FIND_WITHIN_RADIUS = """
            WITH candidates AS (
                SELECT u.id AS unit_id,
                       u.unit_type,
                       l.latitude,
                       l.longitude,
                       l.heading,
                       2 * 6371000.0 * asin(sqrt(least(1.0,
                           power(sin(radians(l.latitude - :latitude) / 2), 2) +
                           cos(radians(:latitude)) * cos(radians(l.latitude)) *
                           power(sin(radians(l.longitude - :longitude) / 2), 2)
                       ))) AS distance_m
                  FROM units u
                  JOIN unit_locations l ON l.unit_id = u.id
                 WHERE u.status = 'AVAILABLE'
                   AND l.updated_at > now() - (:maxAgeSeconds * interval '1 second')
                   AND l.latitude BETWEEN :minLatitude AND :maxLatitude
                   AND (
                       :allLongitudes OR
                       (:crossesAntimeridian AND (l.longitude >= :minLongitude OR l.longitude <= :maxLongitude)) OR
                       (NOT :crossesAntimeridian AND l.longitude BETWEEN :minLongitude AND :maxLongitude)
                   )
            )
            SELECT unit_id, unit_type, latitude, longitude, heading
              FROM candidates
             WHERE distance_m <= :radiusMeters
             ORDER BY distance_m
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public NearbyUnitRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<NearbyUnitResponse> findAvailableWithinRadius(double latitude, double longitude,
                                                              double radiusMeters,
                                                              int locationMaxAgeSeconds) {
        GeoBoundingBox box = GeoBoundingBox.around(latitude, longitude, radiusMeters);
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("latitude", latitude)
                .addValue("longitude", longitude)
                .addValue("radiusMeters", radiusMeters)
                .addValue("maxAgeSeconds", locationMaxAgeSeconds)
                .addValue("minLatitude", box.minLatitude())
                .addValue("maxLatitude", box.maxLatitude())
                .addValue("minLongitude", box.minLongitude())
                .addValue("maxLongitude", box.maxLongitude())
                .addValue("allLongitudes", box.allLongitudes())
                .addValue("crossesAntimeridian", box.crossesAntimeridian());

        return jdbcTemplate.query(FIND_WITHIN_RADIUS, parameters, (resultSet, rowNumber) ->
                new NearbyUnitResponse(
                        resultSet.getObject("unit_id", java.util.UUID.class),
                        UnitType.valueOf(resultSet.getString("unit_type")),
                        resultSet.getDouble("latitude"),
                        resultSet.getDouble("longitude"),
                        (Double) resultSet.getObject("heading")));
    }
}

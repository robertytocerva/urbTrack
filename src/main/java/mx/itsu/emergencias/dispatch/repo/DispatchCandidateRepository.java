package mx.itsu.emergencias.dispatch.repo;

import mx.itsu.emergencias.common.geo.GeoBoundingBox;
import mx.itsu.emergencias.unit.domain.UnitType;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public class DispatchCandidateRepository {

    private static final String FIND_CANDIDATES = """
            WITH candidates AS (
                SELECT u.id AS unit_id,
                       2 * 6371000.0 * asin(sqrt(least(1.0,
                           power(sin(radians(l.latitude - :latitude) / 2), 2) +
                           cos(radians(:latitude)) * cos(radians(l.latitude)) *
                           power(sin(radians(l.longitude - :longitude) / 2), 2)
                       ))) AS distance_m
                  FROM units u
                  JOIN unit_locations l ON l.unit_id = u.id
                 WHERE u.status = 'AVAILABLE'
                   AND u.unit_type = :unitType
                   AND l.updated_at > now() - (:maxAgeSeconds * interval '1 second')
                   AND l.latitude BETWEEN :minLatitude AND :maxLatitude
                   AND (
                       :allLongitudes OR
                       (:crossesAntimeridian AND (l.longitude >= :minLongitude OR l.longitude <= :maxLongitude)) OR
                       (NOT :crossesAntimeridian AND l.longitude BETWEEN :minLongitude AND :maxLongitude)
                   )
                   AND NOT EXISTS (
                       SELECT 1 FROM alert_dispatches d
                        WHERE d.alert_id = :alertId AND d.unit_id = u.id
                   )
            )
            SELECT unit_id, distance_m
              FROM candidates
             WHERE distance_m <= :radiusMeters
             ORDER BY distance_m
             LIMIT :limit
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public DispatchCandidateRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<DispatchCandidate> findCandidates(UUID alertId, double latitude, double longitude,
                                                  UnitType unitType, double radiusMeters,
                                                  int locationMaxAgeSeconds, int limit) {
        GeoBoundingBox box = GeoBoundingBox.around(latitude, longitude, radiusMeters);
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("alertId", alertId)
                .addValue("latitude", latitude)
                .addValue("longitude", longitude)
                .addValue("unitType", unitType.name())
                .addValue("radiusMeters", radiusMeters)
                .addValue("maxAgeSeconds", locationMaxAgeSeconds)
                .addValue("minLatitude", box.minLatitude())
                .addValue("maxLatitude", box.maxLatitude())
                .addValue("minLongitude", box.minLongitude())
                .addValue("maxLongitude", box.maxLongitude())
                .addValue("allLongitudes", box.allLongitudes())
                .addValue("crossesAntimeridian", box.crossesAntimeridian())
                .addValue("limit", limit);
        return jdbcTemplate.query(FIND_CANDIDATES, parameters, (resultSet, rowNumber) ->
                new DispatchCandidate(resultSet.getObject("unit_id", UUID.class),
                        resultSet.getDouble("distance_m")));
    }
}

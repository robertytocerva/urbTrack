package mx.itsu.emergencias.unit.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "unit_locations")
@Getter
@Setter
@NoArgsConstructor
public class UnitLocationEntity {

    @Id
    @Column(name = "unit_id", nullable = false)
    private UUID unitId;

    @Column(nullable = false)
    private double latitude;

    @Column(nullable = false)
    private double longitude;

    private Double heading;

    @Column(name = "speed_kmh")
    private Double speedKmh;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}

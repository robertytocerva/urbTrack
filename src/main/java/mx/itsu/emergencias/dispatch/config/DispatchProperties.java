package mx.itsu.emergencias.dispatch.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

@Validated
@ConfigurationProperties(prefix = "app.dispatch")
public record DispatchProperties(
        @Min(1) int offersPerRound,
        @Min(1) long offerTtlSeconds,
        @NotEmpty List<@Positive Integer> radiusKmRounds,
        @Min(1) int locationMaxAgeSeconds,
        @Min(1000) long expirationScanMs
) {
}

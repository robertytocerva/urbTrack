package mx.itsu.emergencias.routing.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.routing")
public record RoutingProperties(
        @NotBlank String osrmBaseUrl,
        @Min(1) long connectTimeoutMs,
        @Min(1) long readTimeoutMs
) {
}

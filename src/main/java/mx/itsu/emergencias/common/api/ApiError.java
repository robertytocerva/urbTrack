package mx.itsu.emergencias.common.api;

import java.time.Instant;

public record ApiError(Instant timestamp, int status, String code, String message, String path) {
}

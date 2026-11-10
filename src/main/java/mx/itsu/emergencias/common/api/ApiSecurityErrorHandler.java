package mx.itsu.emergencias.common.api;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.Instant;

@Component
public class ApiSecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public ApiSecurityErrorHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         org.springframework.security.core.AuthenticationException exception)
            throws IOException, ServletException {
        write(response, HttpServletResponse.SC_UNAUTHORIZED, "UNAUTHORIZED",
                "Token ausente, inválido o expirado", request.getRequestURI());
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException exception) throws IOException, ServletException {
        write(response, HttpServletResponse.SC_FORBIDDEN, "FORBIDDEN",
                "No tienes permiso para acceder a este recurso", request.getRequestURI());
    }

    private void write(HttpServletResponse response, int status, String code, String message, String path)
            throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), new ApiError(Instant.now(), status, code, message, path));
    }
}

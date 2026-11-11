package mx.itsu.emergencias.auth.api;

import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import mx.itsu.emergencias.auth.service.AuthService;
import mx.itsu.emergencias.user.api.UserProfileResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api")
@Tag(name = "Auth", description = "Registro, autenticación y perfil de cuenta")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/auth/register/citizen")
    @ResponseStatus(HttpStatus.CREATED)
    public UserProfileResponse registerCitizen(@Valid @RequestBody RegisterCitizenRequest request) {
        return authService.registerCitizen(request);
    }

    @PostMapping("/auth/register/unit")
    @ResponseStatus(HttpStatus.CREATED)
    public UserProfileResponse registerUnit(@Valid @RequestBody RegisterUnitRequest request) {
        return authService.registerUnit(request);
    }

    @PostMapping("/auth/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @GetMapping("/me")
    @SecurityRequirement(name = "bearerAuth")
    public UserProfileResponse me(@AuthenticationPrincipal Jwt jwt) {
        return authService.getProfile(UUID.fromString(jwt.getSubject()));
    }
}

package mx.itsu.emergencias.auth.service;

import mx.itsu.emergencias.auth.api.LoginRequest;
import mx.itsu.emergencias.auth.api.LoginResponse;
import mx.itsu.emergencias.auth.api.RegisterCitizenRequest;
import mx.itsu.emergencias.auth.api.RegisterUnitRequest;
import mx.itsu.emergencias.common.exception.ConflictException;
import mx.itsu.emergencias.common.exception.NotFoundException;
import mx.itsu.emergencias.unit.domain.UnitEntity;
import mx.itsu.emergencias.unit.domain.UnitStatus;
import mx.itsu.emergencias.unit.repo.UnitRepository;
import mx.itsu.emergencias.user.api.UserProfileResponse;
import mx.itsu.emergencias.user.domain.Role;
import mx.itsu.emergencias.user.domain.UserEntity;
import mx.itsu.emergencias.user.repo.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.UUID;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final UnitRepository unitRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtEncoder jwtEncoder;
    private final long expirationMinutes;

    public AuthService(UserRepository userRepository, UnitRepository unitRepository,
                       PasswordEncoder passwordEncoder, AuthenticationManager authenticationManager,
                       JwtEncoder jwtEncoder,
                       @Value("${app.jwt.expiration-minutes:60}") long expirationMinutes) {
        this.userRepository = userRepository;
        this.unitRepository = unitRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtEncoder = jwtEncoder;
        this.expirationMinutes = expirationMinutes;
    }

    @Transactional
    public UserProfileResponse registerCitizen(RegisterCitizenRequest request) {
        String email = normalizeEmail(request.email());
        ensureEmailAvailable(email);
        UserEntity user = createUser(email, request.password(), request.fullName(), request.phone(), Role.CITIZEN);
        return toProfile(user, null);
    }

    @Transactional
    public UserProfileResponse registerUnit(RegisterUnitRequest request) {
        String email = normalizeEmail(request.email());
        ensureEmailAvailable(email);
        if (unitRepository.existsByIdentifier(request.identifier())) {
            throw new ConflictException("UNIT_IDENTIFIER_EXISTS", "El identificador de unidad ya está registrado");
        }

        UserEntity user = createUser(email, request.password(), request.fullName(), request.phone(), Role.UNIT);
        UnitEntity unit = new UnitEntity();
        unit.setUser(user);
        unit.setUnitType(request.unitType());
        unit.setIdentifier(request.identifier());
        unit.setStatus(UnitStatus.OFFLINE);
        unit = unitRepository.saveAndFlush(unit);
        return toProfile(user, unit);
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        String email = normalizeEmail(request.email());
        authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(email, request.password()));
        UserEntity user = userRepository.findByEmail(email)
                .orElseThrow(() -> new NotFoundException("Usuario no encontrado"));

        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("urbtrack")
                .issuedAt(now)
                .expiresAt(now.plus(expirationMinutes, ChronoUnit.MINUTES))
                .subject(user.getId().toString())
                .claim("email", user.getEmail())
                .claim("role", user.getRole().name())
                .build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        return new LoginResponse(token, user.getRole(), user.getId());
    }

    @Transactional(readOnly = true)
    public UserProfileResponse getProfile(UUID userId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Usuario no encontrado"));
        UnitEntity unit = user.getRole() == Role.UNIT
                ? unitRepository.findByUserId(userId).orElse(null)
                : null;
        return toProfile(user, unit);
    }

    private UserEntity createUser(String email, String password, String fullName, String phone, Role role) {
        UserEntity user = new UserEntity();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setFullName(fullName.trim());
        user.setPhone(phone == null || phone.isBlank() ? null : phone.trim());
        user.setRole(role);
        user.setEnabled(true);
        return userRepository.saveAndFlush(user);
    }

    private void ensureEmailAvailable(String email) {
        if (userRepository.existsByEmail(email)) {
            throw new ConflictException("EMAIL_ALREADY_EXISTS", "El correo ya está registrado");
        }
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private UserProfileResponse toProfile(UserEntity user, UnitEntity unit) {
        return new UserProfileResponse(
                user.getId(), user.getEmail(), user.getFullName(), user.getPhone(), user.getRole(),
                unit == null ? null : unit.getId(),
                unit == null ? null : unit.getUnitType(),
                unit == null ? null : unit.getIdentifier(),
                unit == null ? null : unit.getStatus());
    }
}

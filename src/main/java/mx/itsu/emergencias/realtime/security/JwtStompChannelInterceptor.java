package mx.itsu.emergencias.realtime.security;

import mx.itsu.emergencias.common.exception.ForbiddenException;
import mx.itsu.emergencias.user.domain.Role;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class JwtStompChannelInterceptor implements ChannelInterceptor {

    private final JwtDecoder jwtDecoder;
    private final WebSocketSubscriptionAuthorizer subscriptionAuthorizer;

    public JwtStompChannelInterceptor(JwtDecoder jwtDecoder,
                                      WebSocketSubscriptionAuthorizer subscriptionAuthorizer) {
        this.jwtDecoder = jwtDecoder;
        this.subscriptionAuthorizer = subscriptionAuthorizer;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) return message;

        StompCommand command = accessor.getCommand();
        if (command == StompCommand.CONNECT || command == StompCommand.STOMP) {
            authenticateConnect(message, accessor);
        } else if (command == StompCommand.SUBSCRIBE) {
            authorizeSubscription(message, accessor);
        } else if (command == StompCommand.SEND) {
            throw rejected(message, "WebSocket solo admite suscripciones; envía comandos por REST");
        } else if (command == StompCommand.DISCONNECT) {
            return message;
        } else if (accessor.getUser() == null) {
            throw rejected(message, "Conexión STOMP no autenticada");
        }
        return message;
    }

    private void authenticateConnect(Message<?> message, StompHeaderAccessor accessor) {
        String header = accessor.getFirstNativeHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ") || header.length() <= 7) {
            throw rejected(message, "CONNECT requiere Authorization: Bearer <jwt>");
        }
        try {
            Jwt jwt = jwtDecoder.decode(header.substring(7));
            Role role = Role.valueOf(jwt.getClaimAsString("role"));
            UsernamePasswordAuthenticationToken authentication = UsernamePasswordAuthenticationToken.authenticated(
                    jwt.getSubject(), jwt,
                    List.of(new SimpleGrantedAuthority("ROLE_" + role.name())));
            accessor.setUser(authentication);
        } catch (JwtException | IllegalArgumentException exception) {
            throw rejected(message, "JWT de STOMP inválido o expirado");
        }
    }

    private void authorizeSubscription(Message<?> message, StompHeaderAccessor accessor) {
        if (accessor.getUser() == null
                || !(accessor.getUser() instanceof UsernamePasswordAuthenticationToken authentication)
                || !authentication.isAuthenticated()) {
            throw rejected(message, "La suscripción requiere autenticación");
        }
        String roleName = authentication.getAuthorities().stream()
                .map(authority -> authority.getAuthority())
                .filter(authority -> authority.startsWith("ROLE_"))
                .map(authority -> authority.substring(5))
                .findFirst()
                .orElseThrow(() -> rejected(message, "La conexión no tiene un rol válido"));
        try {
            subscriptionAuthorizer.authorize(authentication, Role.valueOf(roleName), accessor.getDestination());
        } catch (ForbiddenException | IllegalArgumentException exception) {
            throw rejected(message, "Suscripción no autorizada");
        }
    }

    private MessageDeliveryException rejected(Message<?> message, String reason) {
        return new MessageDeliveryException(message, reason);
    }
}

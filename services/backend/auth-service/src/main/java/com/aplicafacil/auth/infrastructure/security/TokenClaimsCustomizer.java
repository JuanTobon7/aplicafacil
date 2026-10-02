package com.aplicafacil.auth.infrastructure.security;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.oidc.endpoint.OidcParameterNames;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;

import com.aplicafacil.auth.application.port.UserRepository;

/**
 * Claims propios de AplicaFacil en los tokens.
 *
 * <p>Las colecciones de los claims son {@link ArrayList} a propósito: Spring
 * Authorization Server guarda los claims en {@code oauth2_authorization}
 * (JSON con tipos) y al leerlos solo admite tipos permitidos; las listas
 * inmutables de {@code List.of()} / {@code toList()} no lo están.
 *
 * <ul>
 *   <li>{@code sub}: id del usuario (UUID). Para client_credentials, el client_id.</li>
 *   <li>{@code aud} (access token): la audiencia configurada en el cliente
 *       ({@link #AUDIENCES_SETTING}); por defecto {@code aplicafacil-api}.</li>
 *   <li>{@code roles}: {@code ["USER"]}, {@code ["USER","ADMIN"]} o {@code ["SERVICE"]}.</li>
 *   <li>{@code email}: en access e id tokens de usuarios.</li>
 * </ul>
 */
public class TokenClaimsCustomizer implements OAuth2TokenCustomizer<JwtEncodingContext> {

    /** Setting del cliente con las audiencias de sus access tokens (separadas por coma). */
    public static final String AUDIENCES_SETTING = "settings.client.aplicafacil.audiences";

    static final String ROLES_CLAIM = "roles";
    static final String SERVICE_ROLE = "SERVICE";

    private final UserRepository users;
    private final String defaultAudience;

    public TokenClaimsCustomizer(UserRepository users, String defaultAudience) {
        this.users = users;
        this.defaultAudience = defaultAudience;
    }

    @Override
    public void customize(JwtEncodingContext context) {
        boolean accessToken = OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType());
        boolean idToken = OidcParameterNames.ID_TOKEN.equals(context.getTokenType().getValue());
        if (!accessToken && !idToken) {
            return;
        }

        if (accessToken) {
            context.getClaims().audience(audiences(context.getRegisteredClient()));
        }

        if (AuthorizationGrantType.CLIENT_CREDENTIALS.equals(context.getAuthorizationGrantType())) {
            context.getClaims().claim(ROLES_CLAIM, new ArrayList<>(List.of(SERVICE_ROLE)));
            return;
        }

        Authentication principal = context.getPrincipal();
        context.getClaims().claim(ROLES_CLAIM, roles(principal));
        userId(principal)
                .flatMap(users::findById)
                .ifPresent(user -> context.getClaims().claim("email", user.getEmail()));
    }

    private List<String> audiences(RegisteredClient client) {
        Object configured = client.getClientSettings().getSetting(AUDIENCES_SETTING);
        if (configured instanceof String value && !value.isBlank()) {
            return Arrays.stream(value.split(","))
                    .map(String::strip)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toCollection(ArrayList::new));
        }
        return new ArrayList<>(List.of(defaultAudience));
    }

    private static List<String> roles(Authentication principal) {
        return principal.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> authority.startsWith("ROLE_"))
                .map(authority -> authority.substring("ROLE_".length()))
                .sorted()
                .collect(Collectors.toCollection(ArrayList::new));
    }

    /** El nombre del principal es el id del usuario (ver AuthUserDetailsService). */
    private static Optional<UUID> userId(Authentication principal) {
        try {
            return Optional.of(UUID.fromString(principal.getName()));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}

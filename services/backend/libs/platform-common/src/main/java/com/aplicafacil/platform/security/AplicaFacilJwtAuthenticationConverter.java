package com.aplicafacil.platform.security;

import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

/**
 * Convierte un JWT emitido por auth-service en authorities de Spring Security.
 *
 * <ul>
 *   <li>{@code scope} → {@code SCOPE_profile:read}</li>
 *   <li>{@code roles} → {@code ROLE_USER}, {@code ROLE_ADMIN}, {@code ROLE_SERVICE}</li>
 * </ul>
 *
 * El principal es el {@code sub} del token: id del usuario (UUID) o client_id
 * en tokens de servicio.
 */
public class AplicaFacilJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    public static final String ROLES_CLAIM = "roles";

    private final JwtGrantedAuthoritiesConverter scopes = new JwtGrantedAuthoritiesConverter();

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Collection<GrantedAuthority> authorities = Stream
                .concat(scopes.convert(jwt).stream(), roles(jwt).stream())
                .toList();
        return new JwtAuthenticationToken(jwt, authorities, jwt.getSubject());
    }

    private List<GrantedAuthority> roles(Jwt jwt) {
        List<String> roles = jwt.getClaimAsStringList(ROLES_CLAIM);
        if (roles == null) {
            return List.of();
        }
        return roles.stream()
                .map(role -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + role))
                .toList();
    }
}

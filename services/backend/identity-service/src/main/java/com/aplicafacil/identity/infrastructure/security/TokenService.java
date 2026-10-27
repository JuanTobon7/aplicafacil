package com.aplicafacil.identity.infrastructure.security;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

import com.aplicafacil.identity.infrastructure.config.IdentityProperties;
import com.aplicafacil.platform.security.AplicaFacilJwtAuthenticationConverter;

/**
 * Emite los JWT propios de la plataforma (RS256). Los demás servicios los validan con el JWKS,
 * igual que antes con auth-service: {@code iss}, {@code aud = aplicafacil-api} y {@code roles}.
 */
@Component
public class TokenService {

    private final JwtEncoder encoder;
    private final IdentityProperties properties;
    private final Clock clock;

    public TokenService(JwtEncoder encoder, IdentityProperties properties, Clock clock) {
        this.encoder = encoder;
        this.properties = properties;
        this.clock = clock;
    }

    /** Token de acceso de un usuario: {@code sub} = id de app_user. */
    public IssuedToken issueAccessToken(UUID userId) {
        return issue(userId.toString(), List.of("USER"), properties.accessTokenTtl());
    }

    /** Token para llamadas entre servicios ({@code /internal/**}). */
    public IssuedToken issueServiceToken(String clientId) {
        return issue(clientId, List.of("SERVICE"), properties.serviceTokenTtl());
    }

    private IssuedToken issue(String subject, List<String> roles, java.time.Duration ttl) {
        Instant now = clock.instant();
        Instant expiresAt = now.plus(ttl);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .audience(List.of(properties.audience()))
                .subject(subject)
                .issuedAt(now)
                .expiresAt(expiresAt)
                .id(UUID.randomUUID().toString())
                .claim(AplicaFacilJwtAuthenticationConverter.ROLES_CLAIM, roles)
                .build();
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).build();
        String value = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new IssuedToken(value, expiresAt);
    }

    public record IssuedToken(String value, Instant expiresAt) {

        @Override
        public String toString() {
            return "IssuedToken[expiresAt=" + expiresAt + "]";
        }
    }
}

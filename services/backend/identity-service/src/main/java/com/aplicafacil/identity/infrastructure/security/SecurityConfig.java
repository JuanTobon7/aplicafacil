package com.aplicafacil.identity.infrastructure.security;

import java.time.Clock;
import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.web.SecurityFilterChain;

import com.aplicafacil.identity.infrastructure.config.IdentityProperties;
import com.aplicafacil.platform.security.AplicaFacilJwtAuthenticationConverter;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;

/**
 * Seguridad de identity-service: stateless, sin sesión HTTP ni CSRF (el refresh token va en una cookie
 * SameSite=Strict y el gateway exige el token local en cada petición).
 *
 * <ul>
 *   <li>Públicas: listar las cuentas locales, canjear el código de login, refrescar y el JWKS.</li>
 *   <li>{@code /internal/token}: lo protege el secreto de servicio en {@code InternalController}.</li>
 *   <li>{@code /internal/**}: solo tokens de servicio ({@code ROLE_SERVICE}).</li>
 *   <li>El resto exige un JWT de usuario.</li>
 * </ul>
 * La cadena del cliente OAuth2 de LinkedIn ({@code /oauth2/**}, {@code /login/oauth2/**}) se registra
 * aparte y solo si {@code app.linkedin.oauth.enabled=true}.
 */
@Configuration
public class SecurityConfig {

    static final String[] PUBLIC_PATHS = {
            "/auth/config",
            "/auth/accounts",
            "/auth/accounts/*/session",
            "/auth/session",
            "/auth/refresh",
            "/auth/logout",
            "/internal/token",
            "/.well-known/jwks.json",
            "/actuator/health/**",
            "/actuator/info",
    };

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    JwtEncoder jwtEncoder(SigningKeyService keys) {
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(keys.activeKey())));
    }

    /** Valida sus propios tokens con la llave en memoria, sin pedirse el JWKS a sí mismo por HTTP. */
    @Bean
    JwtDecoder jwtDecoder(SigningKeyService keys, IdentityProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(keys.activePublicKey()).build();
        OAuth2TokenValidator<Jwt> audience = new JwtClaimValidator<List<String>>(
                "aud", aud -> aud != null && aud.contains(properties.audience()));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(properties.issuer()), audience));
        return decoder;
    }

    @Bean
    @Order(10)
    SecurityFilterChain apiFilterChain(HttpSecurity http, AplicaFacilJwtAuthenticationConverter converter)
            throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        .requestMatchers("/internal/**").hasRole("SERVICE")
                        .anyRequest().hasRole("USER"))
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(converter)))
                .build();
    }
}

package com.aplicafacil.aiconfig.infrastructure.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

import com.aplicafacil.platform.security.AplicaFacilJwtAuthenticationConverter;

/**
 * {@code /internal/**} solo acepta tokens de servicio (además, {@code InternalProviderConfigController} exige que
 * sea el mcp-gateway); {@code /ai/**} exige un usuario y cada consulta se limita a sus configuraciones.
 */
@Configuration
public class SecurityConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, AplicaFacilJwtAuthenticationConverter converter)
            throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers("/internal/**").hasRole("SERVICE")
                        .anyRequest().hasRole("USER"))
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(converter)))
                .build();
    }
}

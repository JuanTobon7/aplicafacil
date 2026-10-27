package com.aplicafacil.linkedin.infrastructure.config;

import java.time.Clock;

import jakarta.servlet.DispatcherType;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

import com.aplicafacil.platform.security.AplicaFacilJwtAuthenticationConverter;

/**
 * Conectar no exige JWT (conectar = iniciar sesión). Si llega uno, se valida y el intento es una reconexión.
 * {@code /internal/**} solo acepta tokens de servicio; el resto exige un usuario.
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
                        // Envíos SSE posteriores (despacho ASYNC): la petición original ya se autorizó
                        .dispatcherTypeMatchers(DispatcherType.ASYNC, DispatcherType.ERROR).permitAll()
                        .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers(HttpMethod.POST, "/linkedin/connect").permitAll()
                        .requestMatchers("/linkedin/connect/*", "/linkedin/connect/*/events", "/linkedin/browser")
                        .permitAll()
                        .requestMatchers("/internal/**").hasRole("SERVICE")
                        .anyRequest().hasRole("USER"))
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(converter)))
                .build();
    }
}

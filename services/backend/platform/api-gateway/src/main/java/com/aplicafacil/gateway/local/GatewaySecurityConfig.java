package com.aplicafacil.gateway.local;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * El gateway no valida JWT: deja pasar las rutas públicas de identidad (conectar = iniciar sesión) y cada
 * servicio valida el token por su cuenta (ADR 0001, regla 5). Lo que sí exige es el token local
 * ({@link LocalAccessFilter}).
 */
@Configuration
class GatewaySecurityConfig {

    @Bean
    SecurityFilterChain gatewayFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .build();
    }
}

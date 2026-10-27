package com.aplicafacil.identity.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.aplicafacil.identity.application.services.TokenPolicy;

/** Traduce las propiedades a lo que application necesita, para que application no las conozca. */
@Configuration
class TokenPolicyConfig {

    @Bean
    TokenPolicy tokenPolicy(IdentityProperties properties) {
        return new TokenPolicy(properties.refreshTokenTtl(), properties.loginCodeTtl());
    }
}

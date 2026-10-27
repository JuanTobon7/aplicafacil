package com.aplicafacil.aiconfig.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.aplicafacil.aiconfig.application.AiPolicy;

/** Traduce las propiedades a lo que application necesita, para que application no las conozca. */
@Configuration
class AiPolicyConfig {

    @Bean
    AiPolicy aiPolicy(AiConfigProperties properties) {
        return new AiPolicy(properties.providerTypes(), properties.credentialReaders(), properties.requestTimeout(),
                properties.maxConfigsPerUser());
    }
}

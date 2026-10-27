package com.aplicafacil.linkedin.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.aplicafacil.linkedin.application.services.ConnectPolicy;

/** Traduce las propiedades a lo que application necesita, para que application no las conozca. */
@Configuration
class ConnectConfig {

    @Bean
    ConnectPolicy connectPolicy(LinkedInProperties properties) {
        return new ConnectPolicy(properties.loginTimeout());
    }
}

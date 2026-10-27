package com.aplicafacil.profile.infrastructure.config;

import java.time.Clock;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.aplicafacil.profile.application.ai.ProfileAiPolicy;

/** Reloj, scheduling (outbox y vencimiento de tareas de IA) y los límites de la IA como record de application. */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(ProfileAiProperties.class)
class ClockConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    ProfileAiPolicy profileAiPolicy(ProfileAiProperties properties) {
        return new ProfileAiPolicy(properties.timeout(), properties.maxCvChars(),
                properties.maxFileSize() == null ? 0 : properties.maxFileSize().toBytes());
    }
}

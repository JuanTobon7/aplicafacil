package com.aplicafacil.aiconfig.infrastructure.config;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

import com.aplicafacil.aiconfig.domain.ProviderType;

/**
 * @param providerTypes      tipos válidos; agregar uno no exige tocar código de este servicio
 * @param credentialReaders  {@code sub} de los tokens de servicio que pueden leer la API key descifrada
 * @param requestTimeout     tiempo máximo de una solicitud de IA asíncrona antes de cerrarla como TIMED_OUT
 * @param maxConfigsPerUser  límite de configuraciones por persona
 */
@ConfigurationProperties("aplicafacil.ai")
public record AiConfigProperties(
        List<ProviderType> providerTypes,
        List<String> credentialReaders,
        Duration requestTimeout,
        Integer maxConfigsPerUser) {

    public AiConfigProperties {
        providerTypes = providerTypes == null ? List.of() : List.copyOf(providerTypes);
        credentialReaders = credentialReaders == null ? List.of("mcp-gateway") : List.copyOf(credentialReaders);
        if (requestTimeout == null) {
            requestTimeout = Duration.ofMinutes(2);
        }
        if (maxConfigsPerUser == null) {
            maxConfigsPerUser = 20;
        }
    }
}

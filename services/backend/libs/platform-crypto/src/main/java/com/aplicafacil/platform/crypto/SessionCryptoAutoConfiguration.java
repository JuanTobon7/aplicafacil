package com.aplicafacil.platform.crypto;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Registra {@link SessionCryptoService} en todo servicio que dependa de esta librería. La llave se
 * valida al arrancar: si falta o es inválida, el contexto falla con un mensaje que explica cómo generarla.
 */
@AutoConfiguration
@EnableConfigurationProperties(SessionEncryptionProperties.class)
public class SessionCryptoAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    SessionCryptoService sessionCryptoService(SessionEncryptionProperties properties) {
        return new AesGcmSessionCryptoService(properties.key(), properties.keyVersion());
    }
}

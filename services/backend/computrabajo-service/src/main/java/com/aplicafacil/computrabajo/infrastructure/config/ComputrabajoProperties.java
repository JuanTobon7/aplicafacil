package com.aplicafacil.computrabajo.infrastructure.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param loginTimeout        tiempo máximo para iniciar sesión en la ventana
 * @param scraperUrl          scraper NestJS que maneja el navegador (contracts/openapi/scraper-browser.yaml)
 * @param serviceClientSecret secreto para pedir tokens de servicio a identity-service
 */
@ConfigurationProperties("aplicafacil.computrabajo")
public record ComputrabajoProperties(
        Duration loginTimeout,
        String scraperUrl,
        String identityServiceUrl,
        String serviceClientSecret) {

    public ComputrabajoProperties {
        if (loginTimeout == null) {
            loginTimeout = Duration.ofMinutes(5);
        }
        if (scraperUrl == null) {
            scraperUrl = "http://127.0.0.1:8090";
        }
        if (identityServiceUrl == null) {
            identityServiceUrl = "http://127.0.0.1:9000";
        }
    }

    @Override
    public String toString() {
        return "ComputrabajoProperties[scraperUrl=" + scraperUrl + "]";
    }
}

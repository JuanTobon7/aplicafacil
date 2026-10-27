package com.aplicafacil.jobs.infrastructure.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param serviceClientSecret secreto para pedir el token de servicio a identity-service ({@code /internal/token})
 * @param scraperUrl          scraper NestJS: búsquedas en portales públicos (Computrabajo)
 * @param linkedinServiceUrl  linkedin-service: búsquedas con la sesión del usuario
 */
@ConfigurationProperties("aplicafacil.jobs")
public record JobsProperties(
        String identityServiceUrl,
        String linkedinServiceUrl,
        String scraperUrl,
        String serviceClientSecret,
        Duration searchTimeout,
        Worker worker) {

    public JobsProperties {
        if (identityServiceUrl == null) {
            identityServiceUrl = "http://127.0.0.1:9000";
        }
        if (linkedinServiceUrl == null) {
            linkedinServiceUrl = "http://127.0.0.1:8085";
        }
        if (scraperUrl == null) {
            scraperUrl = "http://127.0.0.1:8090";
        }
        if (searchTimeout == null) {
            searchTimeout = Duration.ofMinutes(7);
        }
        if (worker == null) {
            worker = new Worker(true, 2, 3, null, null);
        }
    }

    @Override
    public String toString() {
        return "JobsProperties[worker=" + worker + "]";
    }

    /**
     * @param concurrency  búsquedas a la vez por instancia (cada una es un navegador headless en el scraper)
     * @param maxAttempts  intentos ante fallos transitorios antes de FAILED
     */
    public record Worker(Boolean enabled, int concurrency, int maxAttempts, Duration retryBackoff,
                         Duration maxRetryBackoff) {

        public Worker {
            // Sin la propiedad, el worker está activo: apagarlo es la decisión explícita
            enabled = enabled == null || enabled;
            concurrency = Math.max(1, concurrency);
            maxAttempts = Math.max(1, maxAttempts);
            if (retryBackoff == null) {
                retryBackoff = Duration.ofSeconds(30);
            }
            if (maxRetryBackoff == null) {
                maxRetryBackoff = Duration.ofMinutes(10);
            }
        }
    }
}

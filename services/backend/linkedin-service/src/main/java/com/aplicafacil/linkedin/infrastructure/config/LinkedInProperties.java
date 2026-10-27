package com.aplicafacil.linkedin.infrastructure.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param sessionProvider     LOCAL_PLAYWRIGHT (fase local) o BROWSER_EXTENSION (fase desplegada)
 * @param loginTimeout        tiempo máximo para iniciar sesión (incluye 2FA y captcha)
 * @param sessionLease        tiempo máximo que una ejecución retiene la sesión de un usuario
 * @param serviceClientSecret secreto para pedir tokens de servicio a identity-service
 * @param scraperUrl          scraper NestJS que maneja el navegador (contracts/openapi/scraper-browser.yaml)
 */
@ConfigurationProperties("aplicafacil.linkedin")
public record LinkedInProperties(
        String sessionProvider,
        String loginUrl,
        Duration loginTimeout,
        Duration sessionLease,
        String identityServiceUrl,
        String profileServiceUrl,
        String serviceClientSecret,
        String scraperUrl) {

    public LinkedInProperties {
        if (sessionProvider == null) {
            sessionProvider = "LOCAL_PLAYWRIGHT";
        }
        if (loginUrl == null) {
            loginUrl = "https://www.linkedin.com/login";
        }
        if (loginTimeout == null) {
            loginTimeout = Duration.ofMinutes(5);
        }
        if (scraperUrl == null) {
            scraperUrl = "http://127.0.0.1:8090";
        }
        if (sessionLease == null) {
            sessionLease = Duration.ofMinutes(30);
        }
    }

    @Override
    public String toString() {
        return "LinkedInProperties[sessionProvider=" + sessionProvider + "]";
    }
}

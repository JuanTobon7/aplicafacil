package com.aplicafacil.identity.infrastructure.config;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param issuer              {@code iss} de los JWT (URL con la que los demás servicios lo validan)
 * @param audience            {@code aud} de los JWT
 * @param serviceClients      servicios que pueden pedir un token de servicio en {@code /internal/token}
 * @param serviceClientSecret secreto compartido de esos servicios ({@code SERVICE_CLIENT_SECRET})
 */
@ConfigurationProperties("aplicafacil.identity")
public record IdentityProperties(
        String issuer,
        String audience,
        Duration accessTokenTtl,
        Duration refreshTokenTtl,
        Duration loginCodeTtl,
        Duration serviceTokenTtl,
        String linkedinServiceUrl,
        List<String> serviceClients,
        String serviceClientSecret) {

    public IdentityProperties {
        if (serviceTokenTtl == null) {
            serviceTokenTtl = Duration.ofMinutes(5);
        }
        serviceClients = serviceClients == null ? List.of() : List.copyOf(serviceClients);
    }

    @Override
    public String toString() {
        return "IdentityProperties[issuer=" + issuer + ", audience=" + audience + "]";
    }
}

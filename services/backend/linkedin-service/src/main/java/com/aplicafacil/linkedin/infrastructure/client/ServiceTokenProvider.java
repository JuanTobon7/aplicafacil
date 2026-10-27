package com.aplicafacil.linkedin.infrastructure.client;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.aplicafacil.linkedin.infrastructure.config.LinkedInProperties;

/**
 * Token de servicio de identity-service ({@code /internal/token}) en caché hasta poco antes de vencer.
 * Se agrega como interceptor a los clientes de identity-service y profile-service.
 */
@Component
public class ServiceTokenProvider {

    private static final String CLIENT_ID = "linkedin-service";

    private final RestClient identity;
    private final String secret;
    private final Clock clock;
    private volatile Cached cached;

    public ServiceTokenProvider(RestClient.Builder builder, LinkedInProperties properties, Clock clock) {
        this.identity = builder.clone().baseUrl(properties.identityServiceUrl()).build();
        this.secret = properties.serviceClientSecret();
        this.clock = clock;
    }

    public ClientHttpRequestInterceptor interceptor() {
        return (request, body, execution) -> {
            request.getHeaders().setBearerAuth(token());
            return execution.execute(request, body);
        };
    }

    synchronized String token() {
        Instant now = clock.instant();
        if (cached != null && cached.expiresAt().isAfter(now.plus(Duration.ofSeconds(30)))) {
            return cached.value();
        }
        TokenResponse response = identity.post()
                .uri("/internal/token")
                .headers(h -> h.setBasicAuth(CLIENT_ID, secret == null ? "" : secret))
                .retrieve()
                .body(TokenResponse.class);
        if (response == null) {
            throw new IllegalStateException("identity-service no devolvió un token de servicio");
        }
        cached = new Cached(response.accessToken(), response.expiresAt());
        return cached.value();
    }

    record TokenResponse(String accessToken, Instant expiresAt) {
    }

    private record Cached(String value, Instant expiresAt) {
    }
}

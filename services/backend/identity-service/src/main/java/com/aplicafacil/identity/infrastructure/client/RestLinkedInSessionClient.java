package com.aplicafacil.identity.infrastructure.client;

import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.aplicafacil.identity.application.port.out.LinkedInSessionClient;
import com.aplicafacil.identity.infrastructure.config.IdentityProperties;
import com.aplicafacil.identity.infrastructure.security.TokenService;

/** Cliente de {@code /internal/**} de linkedin-service con un token de servicio firmado aquí mismo. */
@Component
public class RestLinkedInSessionClient implements LinkedInSessionClient {

    private final RestClient client;

    public RestLinkedInSessionClient(RestClient.Builder builder, IdentityProperties properties, TokenService tokens) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(3));
        // Validar abre un navegador headless: puede tardar varios segundos
        requestFactory.setReadTimeout(Duration.ofSeconds(90));
        this.client = builder
                .baseUrl(properties.linkedinServiceUrl())
                .requestFactory(requestFactory)
                .requestInterceptor((request, body, execution) -> {
                    request.getHeaders().setBearerAuth(tokens.issueServiceToken("identity-service").value());
                    return execution.execute(request, body);
                })
                .build();
    }

    @Override
    public SessionCheck validate(UUID userId) {
        ValidationResponse response = client.post()
                .uri("/internal/sessions/{userId}/validate", userId)
                .retrieve()
                .body(ValidationResponse.class);
        return response == null ? SessionCheck.NOT_CONNECTED : response.result();
    }

    @Override
    public void deleteSession(UUID userId) {
        client.delete().uri("/internal/sessions/{userId}", userId).retrieve().toBodilessEntity();
    }

    @Override
    public Map<UUID, String> connectionStatuses(Collection<UUID> userIds) {
        Map<UUID, String> body = client.post()
                .uri("/internal/connections/statuses")
                .body(userIds)
                .retrieve()
                .body(new ParameterizedTypeReference<Map<UUID, String>>() { });
        return body == null ? Map.of() : new HashMap<>(body);
    }

    record ValidationResponse(SessionCheck result) {
    }
}

package com.aplicafacil.linkedin.infrastructure.client;

import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.aplicafacil.linkedin.application.port.out.ProfileSync;
import com.aplicafacil.linkedin.contract.ProfileSnapshot;
import com.aplicafacil.linkedin.infrastructure.config.LinkedInProperties;

/** {@code PUT /internal/profiles/{userId}/sync} de profile-service: respeta las secciones editadas a mano. */
@Component
public class ProfileClient implements ProfileSync {

    private final RestClient client;

    public ProfileClient(RestClient.Builder builder, LinkedInProperties properties, ServiceTokenProvider tokens) {
        this.client = builder.clone()
                .baseUrl(properties.profileServiceUrl())
                .requestInterceptor(tokens.interceptor())
                .build();
    }

    @Override
    public void sync(UUID userId, ProfileSnapshot snapshot) {
        client.put()
                .uri("/internal/profiles/{userId}/sync", userId)
                .body(snapshot)
                .retrieve()
                .toBodilessEntity();
    }
}

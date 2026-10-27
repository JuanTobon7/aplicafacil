package com.aplicafacil.linkedin.infrastructure.client;

import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.aplicafacil.linkedin.application.exception.ConnectException;
import com.aplicafacil.linkedin.application.port.out.AccountProvisioning;
import com.aplicafacil.linkedin.contract.LinkedInIdentity;
import com.aplicafacil.linkedin.infrastructure.config.LinkedInProperties;

/** {@code POST /internal/users/provision} de identity-service (UserProvisioningService). */
@Component
public class IdentityClient implements AccountProvisioning {

    private final RestClient client;

    public IdentityClient(RestClient.Builder builder, LinkedInProperties properties, ServiceTokenProvider tokens) {
        this.client = builder.clone()
                .baseUrl(properties.identityServiceUrl())
                .requestInterceptor(tokens.interceptor())
                .build();
    }

    @Override
    public Provisioned provision(UUID attemptId, LinkedInIdentity identity) {
        Provisioned result;
        try {
            result = client.post()
                    .uri("/internal/users/provision")
                    .body(new ProvisionRequest(attemptId, identity))
                    .retrieve()
                    .body(Provisioned.class);
        } catch (RestClientException e) {
            throw ConnectException.identityServiceUnavailable(e);
        }
        if (result == null) {
            throw new IllegalStateException("identity-service no devolvió el usuario");
        }
        return result;
    }

    record ProvisionRequest(UUID attemptId, LinkedInIdentity identity) {
    }
}

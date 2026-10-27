package com.aplicafacil.identity.api;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.aplicafacil.identity.application.exception.IdentityException;
import com.aplicafacil.identity.application.port.in.UserProvisioningService;
import com.aplicafacil.identity.application.port.in.UserProvisioningService.Outcome;
import com.aplicafacil.identity.application.port.in.UserProvisioningService.ProvisioningResult;
import com.aplicafacil.identity.application.services.LoginCodeService;
import com.aplicafacil.identity.infrastructure.config.IdentityProperties;
import com.aplicafacil.identity.application.services.SecureTokens;
import com.aplicafacil.identity.infrastructure.security.TokenService;
import com.aplicafacil.identity.infrastructure.security.TokenService.IssuedToken;
import com.aplicafacil.linkedin.contract.LinkedInIdentity;

/** Endpoints entre servicios. El gateway no los expone (solo enruta {@code /api/auth/**}). */
@RestController
@RequestMapping("/internal")
class InternalController {

    private final UserProvisioningService provisioning;
    private final LoginCodeService loginCodes;
    private final TokenService tokens;
    private final IdentityProperties properties;

    InternalController(UserProvisioningService provisioning, LoginCodeService loginCodes, TokenService tokens,
                       IdentityProperties properties) {
        this.provisioning = provisioning;
        this.loginCodes = loginCodes;
        this.tokens = tokens;
        this.properties = properties;
    }

    /** Token de servicio (equivale a client_credentials): HTTP Basic con el nombre del servicio y SERVICE_CLIENT_SECRET. */
    @PostMapping("/token")
    ServiceTokenResponse token(@RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        String clientId = authenticateClient(authorization);
        IssuedToken token = tokens.issueServiceToken(clientId);
        return new ServiceTokenResponse(token.value(), token.expiresAt());
    }

    /**
     * linkedin-service, al terminar la captura de la sesión: crea, actualiza o vincula el usuario y entrega
     * un código de un solo uso para que el front inicie sesión.
     */
    @PostMapping("/users/provision")
    ProvisionResponse provision(@Valid @RequestBody ProvisionRequest request) {
        ProvisioningResult result = provisioning.provision(request.identity());
        String code = loginCodes.create(result.userId(), request.attemptId());
        return new ProvisionResponse(result.userId(), result.outcome(), code);
    }

    private String authenticateClient(String authorization) {
        String expectedSecret = properties.serviceClientSecret();
        if (expectedSecret == null || expectedSecret.isBlank()) {
            throw unauthorized();
        }
        BasicCredentials credentials = BasicCredentials.parse(authorization).orElseThrow(InternalController::unauthorized);
        if (!properties.serviceClients().contains(credentials.clientId())
                || !SecureTokens.constantTimeEquals(credentials.secret(), expectedSecret)) {
            throw unauthorized();
        }
        return credentials.clientId();
    }

    /** {@code Authorization: Basic base64(clientId:secret)}. */
    record BasicCredentials(String clientId, String secret) {

        static Optional<BasicCredentials> parse(String authorization) {
            if (authorization == null || !authorization.startsWith("Basic ")) {
                return Optional.empty();
            }
            String decoded;
            try {
                decoded = new String(Base64.getDecoder().decode(authorization.substring(6)), StandardCharsets.UTF_8);
            } catch (IllegalArgumentException e) {
                return Optional.empty();
            }
            int colon = decoded.indexOf(':');
            return colon < 0 ? Optional.empty()
                    : Optional.of(new BasicCredentials(decoded.substring(0, colon), decoded.substring(colon + 1)));
        }

        @Override
        public String toString() {
            return "BasicCredentials[clientId=" + clientId + "]";
        }
    }

    private static IdentityException unauthorized() {
        return new IdentityException(HttpStatus.UNAUTHORIZED, "INVALID_CLIENT", "Credenciales de servicio inválidas");
    }

    record ServiceTokenResponse(String accessToken, Instant expiresAt) {
    }

    record ProvisionRequest(@NotNull UUID attemptId, @NotNull LinkedInIdentity identity) {
    }

    record ProvisionResponse(UUID userId, Outcome outcome, String loginCode) {
    }
}

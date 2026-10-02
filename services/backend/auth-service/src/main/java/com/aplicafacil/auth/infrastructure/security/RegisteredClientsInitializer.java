package com.aplicafacil.auth.infrastructure.security;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.stereotype.Component;

/**
 * Registra (o actualiza) los clientes OAuth de primera parte al arrancar, a
 * partir de {@link AuthProperties}. Los secretos vienen de variables de
 * entorno y se guardan hasheados.
 *
 * <pre>
 *  cliente             tipo          grant
 *  aplicafacil-web     público       authorization_code + PKCE (S256)
 *  automation-service  confidencial  client_credentials
 *  mcp-gateway         confidencial  token-exchange (RFC 8693)
 * </pre>
 */
@Component
@Order(1)
class RegisteredClientsInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(RegisteredClientsInitializer.class);

    private final RegisteredClientRepository clients;
    private final PasswordEncoder passwordEncoder;
    private final AuthProperties properties;

    RegisteredClientsInitializer(RegisteredClientRepository clients, PasswordEncoder passwordEncoder,
                                 AuthProperties properties) {
        this.clients = clients;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        upsert(webClient());
        serviceClient(properties.automationClient(), AuthorizationGrantType.CLIENT_CREDENTIALS);
        serviceClient(properties.mcpGatewayClient(), AuthorizationGrantType.TOKEN_EXCHANGE);
    }

    private RegisteredClient.Builder webClient() {
        AuthProperties.WebClient web = properties.webClient();
        return RegisteredClient.withId(idFor(web.clientId()))
                .clientId(web.clientId())
                .clientName("AplicaFacil web + extensión")
                // cliente público: corre en el navegador, no puede guardar un secreto
                .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUris(uris -> uris.addAll(web.redirectUris()))
                .postLogoutRedirectUris(uris -> uris.addAll(web.postLogoutRedirectUris()))
                .scope(OidcScopes.OPENID)
                .scope(OidcScopes.PROFILE)
                .scope(OidcScopes.EMAIL)
                .clientSettings(ClientSettings.builder()
                        .requireProofKey(true)
                        .requireAuthorizationConsent(false)
                        .setting(TokenClaimsCustomizer.AUDIENCES_SETTING, properties.apiAudience())
                        .build())
                .tokenSettings(tokenSettings());
    }

    private void serviceClient(AuthProperties.ServiceClient client, AuthorizationGrantType grant) {
        if (client == null || !client.isConfigured()) {
            log.warn("Cliente OAuth '{}' sin secreto configurado: no se registra",
                    client == null ? grant.getValue() : client.clientId());
            return;
        }
        upsert(RegisteredClient.withId(idFor(client.clientId()))
                .clientId(client.clientId())
                .clientName(client.clientId())
                .clientSecret(passwordEncoder.encode(client.secret()))
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(grant)
                .scopes(scopes -> scopes.addAll(client.scopes()))
                .clientSettings(ClientSettings.builder()
                        .requireAuthorizationConsent(false)
                        .setting(TokenClaimsCustomizer.AUDIENCES_SETTING, properties.apiAudience())
                        .build())
                .tokenSettings(tokenSettings()));
    }

    private TokenSettings tokenSettings() {
        return TokenSettings.builder()
                .accessTokenTimeToLive(properties.accessTokenTtl())
                .build();
    }

    /** Conserva el id interno si el cliente ya existe: así el save actualiza en vez de duplicar. */
    private String idFor(String clientId) {
        RegisteredClient existing = clients.findByClientId(clientId);
        return existing != null ? existing.getId() : UUID.randomUUID().toString();
    }

    private void upsert(RegisteredClient.Builder builder) {
        RegisteredClient client = builder.build();
        clients.save(client);
        log.info("Cliente OAuth registrado: {} ({})", client.getClientId(), client.getAuthorizationGrantTypes()
                .stream().map(AuthorizationGrantType::getValue).toList());
    }
}

package com.aplicafacil.auth.infrastructure.security;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuración del Authorization Server ({@code aplicafacil.auth.*} en
 * config-repo/auth-service.yml).
 *
 * @param issuer             URL pública del servidor; va en el claim {@code iss}.
 * @param apiAudience        {@code aud} que exigen los microservicios.
 * @param accessTokenTtl     vida de los access tokens.
 * @param corsAllowedOrigins orígenes del navegador que llaman a /oauth2/token, /userinfo…
 * @param webClient          cliente público (web + extensión): Authorization Code + PKCE.
 * @param automationClient   cliente confidencial de automation-service: client_credentials.
 * @param mcpGatewayClient   cliente confidencial del mcp-gateway: token exchange (RFC 8693).
 * @param bootstrapAdmin     admin inicial; se crea al arrancar si no existe.
 */
@ConfigurationProperties("aplicafacil.auth")
public record AuthProperties(
        String issuer,
        String apiAudience,
        Duration accessTokenTtl,
        List<String> corsAllowedOrigins,
        WebClient webClient,
        ServiceClient automationClient,
        ServiceClient mcpGatewayClient,
        BootstrapAdmin bootstrapAdmin) {

    public record WebClient(String clientId, List<String> redirectUris, List<String> postLogoutRedirectUris) {
    }

    /** Un cliente sin {@code secret} no se registra (se avisa en el log). */
    public record ServiceClient(String clientId, String secret, List<String> scopes) {

        public boolean isConfigured() {
            return secret != null && !secret.isBlank();
        }
    }

    /** Sin email o contraseña no se crea ningún admin. */
    public record BootstrapAdmin(String email, String password) {

        public boolean isConfigured() {
            return email != null && !email.isBlank() && password != null && !password.isBlank();
        }
    }
}

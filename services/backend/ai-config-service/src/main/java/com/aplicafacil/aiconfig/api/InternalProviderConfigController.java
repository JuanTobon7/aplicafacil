package com.aplicafacil.aiconfig.api;

import java.util.List;
import java.util.UUID;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.aplicafacil.aiconfig.application.CredentialResolver;
import com.aplicafacil.aiconfig.application.CredentialResolver.ConfigSummary;
import com.aplicafacil.aiconfig.application.CredentialResolver.ResolvedConfig;

/**
 * API para el mcp-gateway (contracts/openapi/ai-config-internal.yaml). El gateway no la expone. Además del rol
 * SERVICE, exige que el token sea de un lector autorizado ({@code aplicafacil.ai.credential-readers}).
 */
@RestController
@RequestMapping("/internal/provider-configs")
class InternalProviderConfigController {

    private final CredentialResolver resolver;

    InternalProviderConfigController(CredentialResolver resolver) {
        this.resolver = resolver;
    }

    @GetMapping
    List<ConfigSummary> list(@AuthenticationPrincipal Jwt jwt, @RequestParam(required = false) UUID userId) {
        authorize(jwt);
        return resolver.summaries(userId);
    }

    @GetMapping("/{configId}")
    ResponseEntity<ResolvedConfig> resolve(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID configId,
                                           @RequestParam UUID userId) {
        String reader = authorize(jwt);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(resolver.resolve(configId, userId, reader));
    }

    private String authorize(Jwt jwt) {
        resolver.authorize(jwt.getSubject());
        return jwt.getSubject();
    }
}

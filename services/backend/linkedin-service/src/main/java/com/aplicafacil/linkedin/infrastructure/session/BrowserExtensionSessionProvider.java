package com.aplicafacil.linkedin.infrastructure.session;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.aplicafacil.linkedin.application.port.out.LinkedInSessionProvider;
import com.aplicafacil.linkedin.domain.LinkedInAction;

/**
 * Fase desplegada (stub): una extensión en el navegador del usuario ejecuta las acciones con su propia
 * sesión de LinkedIn. El servidor solo encola las {@link LinkedInAction} y recibe los resultados;
 * nunca guarda cookies de LinkedIn.
 *
 * <p>Pendiente: cola de trabajos por usuario, canal autenticado con la extensión y timeouts.
 */
@Component
@ConditionalOnProperty(name = "aplicafacil.linkedin.session-provider", havingValue = "BROWSER_EXTENSION")
public class BrowserExtensionSessionProvider implements LinkedInSessionProvider {

    @Override
    public <R> CompletableFuture<R> execute(UUID userId, LinkedInAction<R> action) {
        return CompletableFuture.failedFuture(new UnsupportedOperationException(
                "BrowserExtensionSessionProvider todavía no está implementado (fase desplegada)"));
    }
}

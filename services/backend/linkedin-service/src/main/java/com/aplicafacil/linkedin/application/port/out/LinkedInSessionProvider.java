package com.aplicafacil.linkedin.application.port.out;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.aplicafacil.linkedin.domain.LinkedInAction;

/**
 * Ejecuta acciones de LinkedIn con la sesión de un usuario, sin exponer cómo se obtiene esa sesión.
 *
 * <ul>
 *   <li>{@code LocalPlaywrightSessionProvider} (fase local): descifra el storageState de la BD, crea un
 *       contexto no persistente, ejecuta la acción y vuelve a guardar el storageState renovado.</li>
 *   <li>{@code BrowserExtensionSessionProvider} (fase desplegada): encola la acción para la extensión
 *       del navegador del usuario; el servidor nunca guarda cookies de LinkedIn.</li>
 * </ul>
 *
 * Las acciones se describen como datos ({@link LinkedInAction}), no como código que recibe un
 * navegador. Así la misma acción puede viajar a la extensión.
 */
public interface LinkedInSessionProvider {

    /**
     * @throws com.aplicafacil.linkedin.application.exception.SessionBusyException si otra automatización usa la sesión
     * @throws com.aplicafacil.linkedin.application.exception.SessionExpiredException si la sesión venció o no se pudo descifrar
     */
    <R> CompletableFuture<R> execute(UUID userId, LinkedInAction<R> action);
}

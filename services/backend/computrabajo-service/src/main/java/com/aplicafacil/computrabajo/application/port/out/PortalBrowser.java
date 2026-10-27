package com.aplicafacil.computrabajo.application.port.out;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import com.aplicafacil.computrabajo.domain.ConnectStep;

/**
 * Puerto hacia quien maneja el navegador (el scraper NestJS con Playwright, sin base de datos): recibe el storageState
 * en cada llamada y devuelve el renovado. Contrato HTTP: {@code contracts/openapi/scraper-browser.yaml}.
 */
public interface PortalBrowser {

    enum BrowserState { INSTALLING, READY, FAILED, UNAVAILABLE }

    BrowserState browserState();

    /**
     * Abre el navegador visible y espera a que la persona inicie sesión. Bloquea hasta terminar.
     *
     * @throws com.aplicafacil.computrabajo.application.exception.ConnectException con el código del fallo
     */
    LoginResult login(UUID attemptId, Duration timeout, Consumer<ConnectStep> onStep);

    void cancelLogin(UUID attemptId);

    /**
     * Valida la sesión en un contexto headless no persistente.
     *
     * @return el storageState renovado, o vacío si Computrabajo cerró la sesión
     */
    Optional<String> validate(String storageState);

    /** {@code storageState} es secreto: el toString lo oculta. */
    record LoginResult(String candidateName, String storageState) {
        @Override
        public String toString() {
            return "LoginResult[candidateName=" + candidateName + "]";
        }
    }
}

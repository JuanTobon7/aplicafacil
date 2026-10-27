package com.aplicafacil.linkedin.application.services;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import com.aplicafacil.linkedin.application.exception.ConnectException;
import com.aplicafacil.linkedin.application.port.out.AccountProvisioning.Provisioned;
import com.aplicafacil.linkedin.application.port.out.BrowserAutomation;
import com.aplicafacil.linkedin.application.port.out.BrowserAutomation.BrowserState;
import com.aplicafacil.linkedin.application.port.out.BrowserAutomation.LoginResult;
import com.aplicafacil.linkedin.application.services.ConnectAttempt.AttemptView;
import com.aplicafacil.linkedin.application.services.ConnectionCompleter.Completed;
import com.aplicafacil.linkedin.domain.ConnectionStatus;

/**
 * Conectar = iniciar sesión (ADR 0003). El navegador lo maneja el scraper ({@link BrowserAutomation}); aquí se
 * controla el intento (empezar, cancelar, fallar y restaurar el estado). Lo que sigue al login está en
 * {@link ConnectionCompleter}.
 */
@Service
public class ConnectService {

    private static final Logger log = LoggerFactory.getLogger(ConnectService.class);

    private final BrowserAutomation browser;
    private final ConnectAttemptRegistry attempts;
    private final ConnectionStateService connections;
    private final ConnectionCompleter completer;
    private final ConnectPolicy policy;

    public ConnectService(BrowserAutomation browser, ConnectAttemptRegistry attempts,
                          ConnectionStateService connections, ConnectionCompleter completer, ConnectPolicy policy) {
        this.browser = browser;
        this.attempts = attempts;
        this.connections = connections;
        this.completer = completer;
        this.policy = policy;
    }

    public BrowserState browserState() {
        return browser.browserState();
    }

    /**
     * @param expectedUserId usuario que pide reconectar; {@code null} en un primer ingreso o "Agregar otra cuenta"
     */
    public AttemptView start(UUID expectedUserId) {
        requireBrowserReady();
        ConnectAttempt attempt = attempts.start(expectedUserId);
        ConnectionStatus previous = expectedUserId == null ? null : markConnecting(attempt, expectedUserId);
        Thread.ofVirtual().name("linkedin-connect-" + attempt.id()).start(() -> run(attempt, previous));
        return attempt.view();
    }

    public void cancel(UUID attemptId) {
        attempts.cancel(attemptId);
        browser.cancelLogin(attemptId);
    }

    private void requireBrowserReady() {
        switch (browser.browserState()) {
            case READY -> { }
            case INSTALLING -> throw ConnectException.fromScraper("BROWSER_NOT_READY",
                    "Se está instalando Chromium; espera un momento.");
            case UNAVAILABLE -> throw ConnectException.scraperUnavailable(null);
            case FAILED -> throw ConnectException.fromScraper("BROWSER_NOT_READY",
                    "Falló la instalación de Chromium en el scraper.");
        }
    }

    private ConnectionStatus markConnecting(ConnectAttempt attempt, UUID userId) {
        try {
            return connections.markConnecting(userId);
        } catch (DataAccessException e) {
            ConnectException error = ConnectException.databaseUnavailable(e);
            attempt.fail(error.code(), error.getMessage());
            throw error;
        }
    }

    private void run(ConnectAttempt attempt, ConnectionStatus previous) {
        try {
            LoginResult login = browser.login(attempt.id(), policy.loginTimeout(), attempt::step);
            Completed completed = completer.complete(attempt, login);
            Provisioned provisioned = completed.provisioned();
            UUID userId = provisioned.userId();
            UUID expected = attempt.expectedUserId();
            if (expected != null && !expected.equals(userId)) {
                // Reconexión con otro miembro: se abre esa cuenta y la anterior conserva su estado
                log.info("La reconexión de {} terminó con otra cuenta ({})", expected, userId);
                connections.restore(expected, previous, null);
            }
            attempt.succeed(userId, provisioned.loginCode(), provisioned.outcome(), completed.profileComplete());
            log.info("LinkedIn conectado para el usuario {} ({})", userId, provisioned.outcome());
        } catch (ConnectException e) {
            fail(attempt, previous, e);
        } catch (DataAccessException e) {
            fail(attempt, previous, ConnectException.databaseUnavailable(e));
        } catch (RuntimeException e) {
            log.warn("Fallo inesperado conectando LinkedIn", e);
            fail(attempt, previous, ConnectException.fromScraper("BROWSER_FAILED", "No se pudo completar la conexión."));
        }
    }

    private void fail(ConnectAttempt attempt, ConnectionStatus previous, ConnectException error) {
        log.info("Conexión con LinkedIn fallida: {}", error.code());
        if (attempt.expectedUserId() != null) {
            try {
                connections.restore(attempt.expectedUserId(), previous, error.code());
            } catch (RuntimeException e) {
                log.warn("No se pudo restaurar el estado de {}: {}", attempt.expectedUserId(), e.getMessage());
            }
        }
        attempt.fail(error.code(), error.getMessage());
    }
}

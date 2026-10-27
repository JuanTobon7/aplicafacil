package com.aplicafacil.computrabajo.application.services;

import java.time.Clock;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import com.aplicafacil.computrabajo.application.exception.ConnectException;
import com.aplicafacil.computrabajo.application.port.out.ConnectionRepository;
import com.aplicafacil.computrabajo.application.port.out.PortalBrowser;
import com.aplicafacil.computrabajo.application.port.out.PortalBrowser.LoginResult;
import com.aplicafacil.computrabajo.application.services.ConnectAttempt.AttemptView;
import com.aplicafacil.computrabajo.domain.ComputrabajoConnection;
import com.aplicafacil.computrabajo.domain.ConnectStep;

/**
 * Conectar = la persona inicia sesión en una ventana que abre el scraper; al terminar se guarda la sesión cifrada.
 * Una reconexión reemplaza la sesión anterior.
 */
@Service
public class ConnectService {

    private static final Logger log = LoggerFactory.getLogger(ConnectService.class);

    private final PortalBrowser browser;
    private final ConnectAttemptRegistry attempts;
    private final ConnectionRepository connections;
    private final ConnectPolicy policy;
    private final Clock clock;

    public ConnectService(PortalBrowser browser, ConnectAttemptRegistry attempts, ConnectionRepository connections,
                          ConnectPolicy policy, Clock clock) {
        this.browser = browser;
        this.attempts = attempts;
        this.connections = connections;
        this.policy = policy;
        this.clock = clock;
    }

    public AttemptView start(UUID userId) {
        requireBrowserReady();
        ConnectAttempt attempt = attempts.start(userId);
        Thread.ofVirtual().name("computrabajo-connect-" + attempt.id()).start(() -> run(attempt));
        return attempt.view();
    }

    public AttemptView attempt(UUID userId, UUID attemptId) {
        return attempts.get(userId, attemptId).view();
    }

    public void cancel(UUID userId, UUID attemptId) {
        attempts.get(userId, attemptId).requestCancel();
        browser.cancelLogin(attemptId);
    }

    private void requireBrowserReady() {
        switch (browser.browserState()) {
            case READY -> { }
            case INSTALLING -> throw ConnectException.browserNotReady("Se está instalando Chromium; espera un momento.");
            case UNAVAILABLE -> throw ConnectException.scraperUnavailable(null);
            case FAILED -> throw ConnectException.browserNotReady("Falló la instalación de Chromium en el scraper.");
        }
    }

    /** Corre en su propio hilo: nada se propaga, todo termina en el estado del intento. */
    void run(ConnectAttempt attempt) {
        try {
            LoginResult login = browser.login(attempt.id(), policy.loginTimeout(), attempt::step);
            attempt.step(ConnectStep.SAVING_SESSION);
            connections.save(ComputrabajoConnection.connected(attempt.userId(), login.candidateName(), clock.instant()),
                    login.storageState());
            attempt.succeed(login.candidateName(), clock.instant());
            log.info("Computrabajo conectado para el usuario {}", attempt.userId());
        } catch (ConnectException e) {
            fail(attempt, e);
        } catch (DataAccessException e) {
            fail(attempt, ConnectException.databaseUnavailable(e));
        } catch (RuntimeException e) {
            log.warn("Fallo inesperado conectando Computrabajo", e);
            fail(attempt, ConnectException.browserFailed());
        }
    }

    private void fail(ConnectAttempt attempt, ConnectException error) {
        log.info("Conexión con Computrabajo fallida: {}", error.code());
        attempt.fail(error.code(), error.getMessage(), clock.instant());
    }
}

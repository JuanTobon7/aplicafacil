package com.aplicafacil.computrabajo.application.services;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.aplicafacil.computrabajo.application.exception.ConnectException;
import com.aplicafacil.computrabajo.application.exception.SessionUnreadableException;
import com.aplicafacil.computrabajo.application.port.out.ConnectionRepository;
import com.aplicafacil.computrabajo.application.port.out.PortalBrowser;
import com.aplicafacil.computrabajo.domain.ComputrabajoConnection;
import com.aplicafacil.computrabajo.domain.ConnectionStatus;

/**
 * Comprueba que la sesión guardada siga viva, para que el estado que ve la persona sea real y, si venció, se le pida
 * iniciar sesión de nuevo. Una validación reciente se reusa: abrir la app varias veces no abre un navegador cada vez.
 */
@Service
public class SessionCheckService {

    /** Una validación más reciente que esto se da por buena. */
    static final Duration FRESH = Duration.ofMinutes(10);
    private static final Logger log = LoggerFactory.getLogger(SessionCheckService.class);

    private final ConnectionRepository connections;
    private final PortalBrowser browser;
    private final Clock clock;

    public SessionCheckService(ConnectionRepository connections, PortalBrowser browser, Clock clock) {
        this.connections = connections;
        this.browser = browser;
        this.clock = clock;
    }

    public Validity check(UUID userId) {
        Optional<ComputrabajoConnection> found = connections.find(userId);
        if (found.isEmpty()) {
            return Validity.NOT_CONNECTED;
        }
        ComputrabajoConnection connection = found.get();
        if (connection.status() == ConnectionStatus.EXPIRED) {
            return Validity.EXPIRED;
        }
        Instant now = clock.instant();
        if (connection.validatedAfter(now.minus(FRESH))) {
            return Validity.VALID;
        }
        return validate(connection, now);
    }

    private Validity validate(ComputrabajoConnection connection, Instant now) {
        Optional<String> stored;
        try {
            stored = connections.loadStorageState(connection.userId());
        } catch (SessionUnreadableException e) {
            return expire(connection);
        }
        if (stored.isEmpty()) {
            return Validity.NOT_CONNECTED;
        }
        try {
            Optional<String> renewed = browser.validate(stored.get());
            if (renewed.isEmpty()) {
                return expire(connection);
            }
            connections.save(connection.validated(now), renewed.get());
            return Validity.VALID;
        } catch (ConnectException e) {
            log.warn("No se pudo comprobar la sesión de Computrabajo de {}: {}", connection.userId(), e.code());
            return Validity.UNAVAILABLE;
        }
    }

    private Validity expire(ComputrabajoConnection connection) {
        connections.update(connection.expired());
        log.info("La sesión de Computrabajo de {} venció", connection.userId());
        return Validity.EXPIRED;
    }

    /** contracts/openapi/computrabajo-api.yaml, {@code SessionCheck.result}. */
    public enum Validity { VALID, EXPIRED, NOT_CONNECTED, UNAVAILABLE }
}

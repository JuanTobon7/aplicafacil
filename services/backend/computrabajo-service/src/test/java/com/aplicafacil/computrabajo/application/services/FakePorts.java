package com.aplicafacil.computrabajo.application.services;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import com.aplicafacil.computrabajo.application.exception.ConnectException;
import com.aplicafacil.computrabajo.application.port.out.ConnectionRepository;
import com.aplicafacil.computrabajo.application.port.out.PortalBrowser;
import com.aplicafacil.computrabajo.domain.ComputrabajoConnection;
import com.aplicafacil.computrabajo.domain.ConnectStep;

/** Puertos en memoria para probar los casos de uso sin base de datos ni scraper. */
final class FakePorts {

    private FakePorts() {
    }

    static final class Connections implements ConnectionRepository {
        final Map<UUID, ComputrabajoConnection> rows = new HashMap<>();
        final Map<UUID, String> states = new HashMap<>();
        RuntimeException loadFailure;

        @Override
        public Optional<ComputrabajoConnection> find(UUID userId) {
            return Optional.ofNullable(rows.get(userId));
        }

        @Override
        public void save(ComputrabajoConnection connection, String storageState) {
            rows.put(connection.userId(), connection);
            states.put(connection.userId(), storageState);
        }

        @Override
        public void update(ComputrabajoConnection connection) {
            rows.computeIfPresent(connection.userId(), (id, old) -> connection);
        }

        @Override
        public Optional<String> loadStorageState(UUID userId) {
            if (loadFailure != null) {
                throw loadFailure;
            }
            return Optional.ofNullable(states.get(userId));
        }

        @Override
        public void delete(UUID userId) {
            rows.remove(userId);
            states.remove(userId);
        }
    }

    static final class Browser implements PortalBrowser {
        BrowserState state = BrowserState.READY;
        LoginResult login = new LoginResult("Ana Pérez", "{\"cookies\":[]}");
        RuntimeException loginFailure;
        Optional<String> validation = Optional.of("{\"renewed\":true}");
        ConnectException validationFailure;
        int validations;
        UUID cancelled;

        @Override
        public BrowserState browserState() {
            return state;
        }

        @Override
        public LoginResult login(UUID attemptId, Duration timeout, Consumer<ConnectStep> onStep) {
            onStep.accept(ConnectStep.WAITING_FOR_LOGIN);
            if (loginFailure != null) {
                throw loginFailure;
            }
            return login;
        }

        @Override
        public void cancelLogin(UUID attemptId) {
            cancelled = attemptId;
        }

        @Override
        public Optional<String> validate(String storageState) {
            validations++;
            if (validationFailure != null) {
                throw validationFailure;
            }
            return validation;
        }
    }
}

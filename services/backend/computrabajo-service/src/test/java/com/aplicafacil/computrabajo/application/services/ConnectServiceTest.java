package com.aplicafacil.computrabajo.application.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.aplicafacil.computrabajo.application.exception.ConnectException;
import com.aplicafacil.computrabajo.application.port.out.PortalBrowser.BrowserState;
import com.aplicafacil.computrabajo.application.services.ConnectAttempt.State;
import com.aplicafacil.computrabajo.domain.ConnectionStatus;

class ConnectServiceTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-10-24T10:00:00Z"), ZoneOffset.UTC);
    private final FakePorts.Connections connections = new FakePorts.Connections();
    private final FakePorts.Browser browser = new FakePorts.Browser();
    private final ConnectAttemptRegistry attempts = new ConnectAttemptRegistry(clock);
    private final ConnectService service = new ConnectService(browser, attempts, connections,
            new ConnectPolicy(Duration.ofMinutes(5)), clock);
    private final UUID userId = UUID.randomUUID();

    @Test
    void aSuccessfulLoginSavesTheSessionForTheUserWhoAsked() {
        ConnectAttempt attempt = attempts.start(userId);

        service.run(attempt);

        assertThat(attempt.view().state()).isEqualTo(State.CONNECTED);
        assertThat(attempt.view().candidateName()).isEqualTo("Ana Pérez");
        assertThat(connections.rows.get(userId).status()).isEqualTo(ConnectionStatus.CONNECTED);
        assertThat(connections.states.get(userId)).isEqualTo("{\"cookies\":[]}");
    }

    @Test
    void aBrowserErrorEndsTheAttemptWithItsCodeAndSavesNothing() {
        browser.loginFailure = new ConnectException("WINDOW_CLOSED", "Cerraste la ventana");
        ConnectAttempt attempt = attempts.start(userId);

        service.run(attempt);

        assertThat(attempt.view().state()).isEqualTo(State.FAILED);
        assertThat(attempt.view().errorCode()).isEqualTo("WINDOW_CLOSED");
        assertThat(connections.rows).isEmpty();
    }

    @Test
    void cancellingClosesTheWindowAndEndsAsCancelled() {
        ConnectAttempt attempt = attempts.start(userId);
        service.cancel(userId, attempt.id());
        browser.loginFailure = new ConnectException("CANCELLED", "Cancelado");

        service.run(attempt);

        assertThat(browser.cancelled).isEqualTo(attempt.id());
        assertThat(attempt.view().state()).isEqualTo(State.CANCELLED);
    }

    @Test
    void anotherPersonCannotSeeOrCancelTheAttempt() {
        ConnectAttempt attempt = attempts.start(userId);
        UUID stranger = UUID.randomUUID();

        assertThatThrownBy(() -> service.attempt(stranger, attempt.id()))
                .isInstanceOfSatisfying(ConnectException.class, e -> assertThat(e.code()).isEqualTo("ATTEMPT_NOT_FOUND"));
        assertThatThrownBy(() -> service.cancel(stranger, attempt.id())).isInstanceOf(ConnectException.class);
    }

    @Test
    void onlyOneWindowAtATimeAndNoneWithoutABrowser() {
        attempts.start(userId);
        assertThatThrownBy(() -> attempts.start(UUID.randomUUID()))
                .isInstanceOfSatisfying(ConnectException.class, e -> assertThat(e.code()).isEqualTo("ALREADY_CONNECTING"));

        browser.state = BrowserState.UNAVAILABLE;
        assertThatThrownBy(() -> service.start(userId))
                .isInstanceOfSatisfying(ConnectException.class, e -> assertThat(e.code()).isEqualTo("SCRAPER_UNAVAILABLE"));
    }
}

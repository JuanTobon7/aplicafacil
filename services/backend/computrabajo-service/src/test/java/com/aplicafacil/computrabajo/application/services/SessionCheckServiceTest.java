package com.aplicafacil.computrabajo.application.services;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.aplicafacil.computrabajo.application.exception.ConnectException;
import com.aplicafacil.computrabajo.application.exception.SessionUnreadableException;
import com.aplicafacil.computrabajo.application.services.SessionCheckService.Validity;
import com.aplicafacil.computrabajo.domain.ComputrabajoConnection;
import com.aplicafacil.computrabajo.domain.ConnectionStatus;

class SessionCheckServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-24T10:00:00Z");

    private final FakePorts.Connections connections = new FakePorts.Connections();
    private final FakePorts.Browser browser = new FakePorts.Browser();
    private final SessionCheckService service = new SessionCheckService(connections, browser,
            Clock.fixed(NOW, ZoneOffset.UTC));
    private final UUID userId = UUID.randomUUID();

    private void connectedAt(Instant validatedAt) {
        connections.save(ComputrabajoConnection.connected(userId, "Ana", validatedAt), "{\"old\":true}");
    }

    @Test
    void withoutAConnectionThereIsNothingToCheck() {
        assertThat(service.check(userId)).isEqualTo(Validity.NOT_CONNECTED);
        assertThat(browser.validations).isZero();
    }

    @Test
    void aRecentValidationIsReusedWithoutOpeningTheBrowser() {
        connectedAt(NOW.minus(SessionCheckService.FRESH).plusSeconds(5));

        assertThat(service.check(userId)).isEqualTo(Validity.VALID);
        assertThat(browser.validations).isZero();
    }

    @Test
    void anOldSessionThatIsStillAliveIsRenewed() {
        connectedAt(NOW.minusSeconds(3600));

        assertThat(service.check(userId)).isEqualTo(Validity.VALID);
        assertThat(connections.states.get(userId)).isEqualTo("{\"renewed\":true}");
        assertThat(connections.rows.get(userId).lastValidatedAt()).isEqualTo(NOW);
    }

    @Test
    void aClosedSessionIsMarkedExpiredAndStaysExpired() {
        connectedAt(NOW.minusSeconds(3600));
        browser.validation = Optional.empty();

        assertThat(service.check(userId)).isEqualTo(Validity.EXPIRED);
        assertThat(connections.rows.get(userId).status()).isEqualTo(ConnectionStatus.EXPIRED);
        assertThat(service.check(userId)).isEqualTo(Validity.EXPIRED);
        assertThat(browser.validations).isEqualTo(1);
    }

    @Test
    void anUnreadableSessionExpiresAndAnUnreachableScraperChangesNothing() {
        connectedAt(NOW.minusSeconds(3600));
        browser.validationFailure = ConnectException.scraperUnavailable(null);
        assertThat(service.check(userId)).isEqualTo(Validity.UNAVAILABLE);
        assertThat(connections.rows.get(userId).status()).isEqualTo(ConnectionStatus.CONNECTED);

        connections.loadFailure = new SessionUnreadableException(new IllegalStateException("llave"));
        assertThat(service.check(userId)).isEqualTo(Validity.EXPIRED);
    }
}

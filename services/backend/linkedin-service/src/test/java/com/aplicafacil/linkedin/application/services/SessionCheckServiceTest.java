package com.aplicafacil.linkedin.application.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.Test;

import com.aplicafacil.linkedin.application.exception.SessionBusyException;
import com.aplicafacil.linkedin.application.port.out.LinkedInSessionProvider;
import com.aplicafacil.linkedin.application.port.out.LinkedInSessionRepository;
import com.aplicafacil.linkedin.application.services.SessionCheckService.Validity;
import com.aplicafacil.linkedin.domain.LinkedInAction.SessionCheck;

/** Comprobar la sesión al abrir la app: reusar una validación reciente y traducir cada resultado. */
class SessionCheckServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-24T12:00:00Z");

    private final LinkedInSessionProvider provider = mock(LinkedInSessionProvider.class);
    private final LinkedInSessionRepository sessions = mock(LinkedInSessionRepository.class);
    private final SessionCheckService service = new SessionCheckService(provider, sessions,
            Clock.fixed(NOW, ZoneOffset.UTC));
    private final UUID userId = UUID.randomUUID();

    @Test
    void withoutASavedSessionItAsksToConnect() throws Exception {
        when(sessions.exists(userId)).thenReturn(false);

        assertThat(service.check(userId)).isEqualTo(Validity.NOT_CONNECTED);
        verify(provider, never()).execute(any(), any());
    }

    @Test
    void aRecentValidationIsReusedWithoutOpeningTheBrowser() throws Exception {
        savedSession(NOW.minusSeconds(60));

        assertThat(service.check(userId)).isEqualTo(Validity.VALID);
        verify(provider, never()).execute(any(), any());
    }

    @Test
    void anOldValidationIsCheckedAgainstLinkedIn() throws Exception {
        savedSession(NOW.minus(SessionCheckService.FRESH).minusSeconds(1));
        when(provider.<SessionCheck>execute(eq(userId), any())).thenReturn(
                CompletableFuture.completedFuture(SessionCheck.EXPIRED));

        assertThat(service.check(userId)).isEqualTo(Validity.EXPIRED);
    }

    @Test
    void aBusySessionIsNotReportedAsExpired() throws Exception {
        savedSession(null);
        when(provider.<SessionCheck>execute(eq(userId), any())).thenReturn(
                CompletableFuture.failedFuture(new SessionBusyException(userId)));

        assertThat(service.check(userId)).isEqualTo(Validity.BUSY);
    }

    @Test
    void anUnknownFailureMeansTheCheckCouldNotBeDone() throws Exception {
        savedSession(null);
        when(provider.<SessionCheck>execute(eq(userId), any())).thenReturn(
                CompletableFuture.failedFuture(new IllegalStateException("scraper caído")));

        assertThat(service.check(userId)).isEqualTo(Validity.UNAVAILABLE);
    }

    private void savedSession(Instant lastValidatedAt) {
        when(sessions.exists(userId)).thenReturn(true);
        when(sessions.lastValidatedAt(userId)).thenReturn(Optional.ofNullable(lastValidatedAt));
    }
}

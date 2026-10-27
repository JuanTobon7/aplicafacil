package com.aplicafacil.linkedin.infrastructure.session;

import java.time.Clock;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.aplicafacil.linkedin.application.exception.NotConnectedException;
import com.aplicafacil.linkedin.application.exception.SessionBusyException;
import com.aplicafacil.linkedin.application.exception.SessionExpiredException;
import com.aplicafacil.linkedin.application.port.out.BrowserAutomation;
import com.aplicafacil.linkedin.application.port.out.BrowserAutomation.JobSearchResult;
import com.aplicafacil.linkedin.application.port.out.BrowserAutomation.ProfileResult;
import com.aplicafacil.linkedin.application.port.out.BrowserAutomation.SessionResult;
import com.aplicafacil.linkedin.application.port.out.LinkedInSessionProvider;
import com.aplicafacil.linkedin.application.port.out.LinkedInSessionRepository;
import com.aplicafacil.linkedin.application.port.out.LinkedInSessionRepository.SessionLease;
import com.aplicafacil.linkedin.application.services.ConnectionStateService;
import com.aplicafacil.linkedin.domain.JobListing;
import com.aplicafacil.linkedin.domain.JobSearchCriteria;
import com.aplicafacil.linkedin.domain.LinkedInAction;
import com.aplicafacil.linkedin.domain.LinkedInAction.ReadProfile;
import com.aplicafacil.linkedin.domain.LinkedInAction.SearchJobs;
import com.aplicafacil.linkedin.domain.LinkedInAction.SessionCheck;
import com.aplicafacil.linkedin.domain.LinkedInAction.ValidateSession;
import com.aplicafacil.linkedin.infrastructure.config.LinkedInProperties;
import com.aplicafacil.platform.crypto.SessionDecryptionException;

/**
 * Fase local: Playwright corre en este equipo dentro del scraper NestJS; este proxy pone lo que el scraper no
 * tiene (BD, cifrado y bloqueo). Por cada acción:
 * <ol>
 *   <li>Toma el arrendamiento del usuario (una automatización a la vez por sesión).</li>
 *   <li>Lee la sesión de la BD y la descifra en memoria; si la llave cambió, marca EXPIRED.</li>
 *   <li>El scraper crea un contexto NO persistente con ese storageState, valida y ejecuta la acción.</li>
 *   <li>Guarda el storageState renovado que devuelve (LinkedIn renueva cookies).</li>
 * </ol>
 */
@Component
@ConditionalOnProperty(name = "aplicafacil.linkedin.session-provider", havingValue = "LOCAL_PLAYWRIGHT",
        matchIfMissing = true)
public class LocalPlaywrightSessionProvider implements LinkedInSessionProvider {

    private static final Logger log = LoggerFactory.getLogger(LocalPlaywrightSessionProvider.class);

    private final LinkedInSessionRepository sessions;
    private final ConnectionStateService connections;
    private final BrowserAutomation browser;
    private final LinkedInProperties properties;
    private final Clock clock;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public LocalPlaywrightSessionProvider(LinkedInSessionRepository sessions, ConnectionStateService connections,
                                          BrowserAutomation browser, LinkedInProperties properties, Clock clock) {
        this.sessions = sessions;
        this.connections = connections;
        this.browser = browser;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public <R> CompletableFuture<R> execute(UUID userId, LinkedInAction<R> action) {
        return CompletableFuture.supplyAsync(() -> run(userId, action), executor);
    }

    private <R> R run(UUID userId, LinkedInAction<R> action) {
        if (!sessions.exists(userId)) {
            throw new NotConnectedException(userId);
        }
        SessionLease lease = sessions.tryAcquireLease(userId, properties.sessionLease())
                .orElseThrow(() -> new SessionBusyException(userId));
        try {
            String storageState = decrypt(userId);
            return switch (action) {
                case ValidateSession v -> cast(validate(userId, storageState));
                case ReadProfile r -> cast(readProfile(userId, storageState));
                case SearchJobs s -> cast(searchJobs(userId, storageState, s.criteria()));
            };
        } finally {
            sessions.releaseLease(lease);
        }
    }

    private SessionCheck validate(UUID userId, String storageState) {
        SessionResult result = browser.validate(storageState);
        if (!result.valid()) {
            connections.markExpired(userId, "SESSION_EXPIRED");
            return SessionCheck.EXPIRED;
        }
        sessions.saveStorageState(userId, result.storageState());
        sessions.markValidated(userId, clock.instant());
        return SessionCheck.VALID;
    }

    private Object readProfile(UUID userId, String storageState) {
        ProfileResult result = browser.readProfile(storageState);
        if (!result.valid()) {
            connections.markExpired(userId, "SESSION_EXPIRED");
            throw new SessionExpiredException(userId);
        }
        sessions.saveStorageState(userId, result.storageState());
        return result.profile();
    }

    private List<JobListing> searchJobs(UUID userId, String storageState, JobSearchCriteria criteria) {
        JobSearchResult result = browser.searchJobs(storageState, criteria);
        if (!result.valid()) {
            connections.markExpired(userId, "SESSION_EXPIRED");
            throw new SessionExpiredException(userId);
        }
        sessions.saveStorageState(userId, result.storageState());
        return result.jobs();
    }

    private String decrypt(UUID userId) {
        try {
            return sessions.loadStorageState(userId).orElseThrow(() -> new NotConnectedException(userId));
        } catch (SessionDecryptionException e) {
            log.warn("No se pudo descifrar la sesión de {} (¿cambió SESSION_ENCRYPTION_KEY?): se marca EXPIRED", userId);
            connections.markExpired(userId, "DECRYPTION_FAILED");
            throw new SessionExpiredException(userId);
        }
    }

    @SuppressWarnings("unchecked")
    private static <R> R cast(Object value) {
        return (R) value;
    }
}

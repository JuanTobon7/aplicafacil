package com.aplicafacil.jobs.application;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aplicafacil.jobs.application.JobSearchRejectedException.Reason;
import com.aplicafacil.jobs.application.port.out.FoundPosting;
import com.aplicafacil.jobs.application.port.out.JobSearchCommands;
import com.aplicafacil.jobs.application.port.out.JobSearchRepository;
import com.aplicafacil.jobs.domain.JobPlatform;
import com.aplicafacil.jobs.domain.SearchCriteria;

/**
 * Casos de uso que ve el front: pedir una búsqueda (la encola para el worker) y consultar su estado y resultados.
 * Todo se limita al usuario del token.
 */
@Service
public class JobSearchService {

    /** Una persona no puede tener más de estas búsquedas esperando o corriendo. */
    static final int MAX_PENDING_PER_USER = 5;
    private static final int RECENT_LIMIT = 20;

    private final JobSearchRepository searches;
    private final JobSearchCommands commands;
    private final JobPortalGateways gateways;
    private final Clock clock;

    public JobSearchService(JobSearchRepository searches, JobSearchCommands commands, JobPortalGateways gateways,
                            Clock clock) {
        this.searches = searches;
        this.commands = commands;
        this.gateways = gateways;
        this.clock = clock;
    }

    @Transactional
    public JobSearchView request(UUID userId, JobPlatform platform, SearchCriteria criteria) {
        if (!gateways.supported().contains(platform)) {
            throw new JobSearchRejectedException(Reason.UNSUPPORTED_PLATFORM, "Plataforma no soportada: " + platform);
        }
        long pending = searches.countPending(userId);
        if (pending >= MAX_PENDING_PER_USER) {
            throw new JobSearchRejectedException(Reason.TOO_MANY_PENDING,
                    "Ya tienes " + pending + " búsquedas en curso; espera a que terminen.");
        }
        JobSearchView created = searches.enqueue(userId, platform, criteria, clock.instant());
        // Misma transacción: el comando queda en el outbox y se publica en RabbitMQ al confirmarse
        commands.requestFirstRun(created.id(), userId);
        return created;
    }

    @Transactional(readOnly = true)
    public JobSearchView get(UUID userId, UUID searchId) {
        return searches.findByIdAndUserId(searchId, userId)
                .orElseThrow(() -> new JobSearchRejectedException(Reason.NOT_FOUND, "La búsqueda no existe"));
    }

    @Transactional(readOnly = true)
    public List<JobSearchView> recent(UUID userId) {
        return searches.recent(userId, RECENT_LIMIT);
    }

    @Transactional(readOnly = true)
    public List<FoundPosting> results(UUID userId, UUID searchId) {
        get(userId, searchId);
        return searches.postingsOf(searchId);
    }
}

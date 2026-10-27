package com.aplicafacil.jobs.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aplicafacil.jobs.application.port.out.JobSearchCommands;
import com.aplicafacil.jobs.application.port.out.JobSearchRepository;
import com.aplicafacil.jobs.domain.JobPlatform;
import com.aplicafacil.jobs.domain.SearchCriteria;

/**
 * Lanza las búsquedas que eligió la IA para el perfil ({@code profile.search-plan.ready}): cada una en cada portal,
 * la principal primero, sin pasar el tope de búsquedas en curso por persona. Un plan ya lanzado se ignora.
 */
@Service
public class SearchPlanService {

    /** Ofertas por búsqueda: lo mismo que el formulario propone por defecto. */
    static final int MAX_RESULTS_PER_SEARCH = 25;
    private static final Logger log = LoggerFactory.getLogger(SearchPlanService.class);

    private final JobSearchRepository searches;
    private final JobSearchCommands commands;
    private final JobPortalGateways gateways;
    private final Clock clock;

    public SearchPlanService(JobSearchRepository searches, JobSearchCommands commands, JobPortalGateways gateways,
                             Clock clock) {
        this.searches = searches;
        this.commands = commands;
        this.gateways = gateways;
        this.clock = clock;
    }

    /** @return cuántas búsquedas quedaron encoladas */
    @Transactional
    public int launch(SearchPlan plan) {
        if (searches.planLaunched(plan.planId())) {
            log.info("El plan {} ya se había lanzado: se ignora", plan.planId());
            return 0;
        }
        long slots = JobSearchService.MAX_PENDING_PER_USER - searches.countPending(plan.userId());
        List<JobPlatform> platforms = gateways.supported().stream().sorted().toList();
        Instant now = clock.instant();
        int queued = 0;
        for (String keywords : plan.searches()) {
            for (JobPlatform platform : platforms) {
                if (queued >= slots) {
                    log.warn("Plan {}: sin cupo para más búsquedas en curso; se lanzaron {}", plan.planId(), queued);
                    return queued;
                }
                enqueue(plan, platform, new SearchCriteria(keywords, plan.location(), MAX_RESULTS_PER_SEARCH,
                        plan.postedWithinDays(), plan.remoteOnly()), now);
                queued++;
            }
        }
        log.info("Plan {} de la IA: {} búsquedas encoladas", plan.planId(), queued);
        return queued;
    }

    private void enqueue(SearchPlan plan, JobPlatform platform, SearchCriteria criteria, Instant now) {
        JobSearchView created = searches.enqueue(plan.userId(), platform, criteria, now, plan.planId());
        // Misma transacción: el comando queda en el outbox y se publica en RabbitMQ al confirmarse
        commands.requestFirstRun(created.id(), plan.userId());
    }

    /** contracts/schemas/search-plan-ready-event.schema.json */
    public record SearchPlan(UUID planId, UUID userId, List<String> searches, boolean remoteOnly,
                             Integer postedWithinDays, String location) {

        public SearchPlan {
            searches = List.copyOf(searches);
        }
    }
}

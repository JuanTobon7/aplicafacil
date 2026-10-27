package com.aplicafacil.jobs.application;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.aplicafacil.jobs.application.port.out.JobPortalGateway;
import com.aplicafacil.jobs.application.port.out.JobPostingStore;
import com.aplicafacil.jobs.domain.JobCard;
import com.aplicafacil.jobs.domain.JobSearch;

/**
 * Fachada (Facade) de la búsqueda de vacantes: el worker la llama con una sola operación y ella coordina los
 * subsistemas.
 *
 * <ol>
 *   <li>elige la estrategia del portal ({@link JobPortalGateways} → {@link JobPortalGateway})</li>
 *   <li>pide la búsqueda: el portal abre el navegador, busca y recorre las cards (scraper, y linkedin-service si
 *       hace falta la sesión)</li>
 *   <li>descarta cards incompletas y repetidas, respetando el orden y {@code maxResults}</li>
 *   <li>guarda las ofertas ({@link JobPostingStore}) y devuelve cuántas encontró y cuántas eran nuevas</li>
 * </ol>
 *
 * No conoce HTTP, RabbitMQ ni la cola: los errores de los portales salen como {@code JobPortalException}.
 */
@Service
public class JobSearchFacade {

    private static final Logger log = LoggerFactory.getLogger(JobSearchFacade.class);

    private final JobPortalGateways gateways;
    private final JobPostingStore postings;

    public JobSearchFacade(JobPortalGateways gateways, JobPostingStore postings) {
        this.gateways = gateways;
        this.postings = postings;
    }

    public Outcome run(JobSearch search) {
        JobPortalGateway gateway = gateways.get(search.platform());
        List<JobCard> cards = gateway.search(search.userId(), search.criteria());

        Map<String, JobCard> unique = new LinkedHashMap<>();
        for (JobCard card : cards) {
            if (unique.size() >= search.criteria().maxResults()) {
                break;
            }
            if (card != null && card.isComplete()) {
                unique.putIfAbsent(card.externalId(), card);
            }
        }
        List<JobCard> kept = List.copyOf(unique.values());
        int created = kept.isEmpty() ? 0 : postings.saveAll(search.id(), search.platform(), kept);
        log.info("Búsqueda {} en {}: {} ofertas ({} nuevas, {} descartadas)", search.id(), search.platform(),
                kept.size(), created, cards.size() - kept.size());
        return new Outcome(kept.size(), created);
    }

    public record Outcome(int found, int created) {
    }
}

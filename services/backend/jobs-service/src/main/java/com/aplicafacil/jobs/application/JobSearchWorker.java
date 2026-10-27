package com.aplicafacil.jobs.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aplicafacil.jobs.application.JobSearchFacade.Outcome;
import com.aplicafacil.jobs.application.JobSearchOutcomes.Failure;
import com.aplicafacil.jobs.application.port.out.JobPortalException;
import com.aplicafacil.jobs.application.port.out.JobSearchStates;
import com.aplicafacil.jobs.domain.JobSearch;

/**
 * Ejecuta un intento de una búsqueda que llegó por RabbitMQ ({@code jobs.search.requested}). Cuántos a la vez y en qué
 * orden lo decide el consumidor de la cola; aquí se decide qué pasa con el resultado:
 *
 * <ul>
 *   <li>Éxito → COMPLETED con cuántas ofertas encontró y cuántas eran nuevas.</li>
 *   <li>Fallo transitorio (portal caído, sesión ocupada…) → QUEUED y el siguiente intento espera su backoff en una
 *       cola de espera.</li>
 *   <li>Fallo definitivo (sin sesión de LinkedIn, sesión vencida) o intentos agotados → FAILED con el código.</li>
 *   <li>Un mensaje repetido o atrasado (ya terminada, borrada o en otro intento) se ignora.</li>
 * </ul>
 */
public class JobSearchWorker {

    private static final Logger log = LoggerFactory.getLogger(JobSearchWorker.class);
    private static final int MAX_MESSAGE = 500;

    private final JobSearchStates states;
    private final JobSearchFacade facade;
    private final JobSearchOutcomes outcomes;
    private final RetryPolicy retries;
    private final Clock clock;

    public JobSearchWorker(JobSearchStates states, JobSearchFacade facade, JobSearchOutcomes outcomes,
                           RetryPolicy retries, Clock clock) {
        this.states = states;
        this.facade = facade;
        this.outcomes = outcomes;
        this.retries = retries;
        this.clock = clock;
    }

    /** @param attempt el intento que pide el mensaje (1 = el primero) */
    public void handle(UUID searchId, int attempt) {
        Optional<JobSearch> started = states.start(searchId, attempt, clock.instant());
        if (started.isEmpty()) {
            log.debug("Intento {} de la búsqueda {} ignorado: ya no corresponde", attempt, searchId);
            return;
        }
        JobSearch search = started.get();
        log.info("Tomada la búsqueda {} en {} (\"{}\", intento {})", search.id(), search.platform(),
                search.criteria().keywords(), search.attempts());
        process(search);
    }

    void process(JobSearch search) {
        try {
            Outcome outcome = facade.run(search);
            outcomes.completed(search, outcome.found(), outcome.created(), clock.instant());
        } catch (JobPortalException e) {
            handleFailure(search, new Failure(e.code(), truncate(e.getMessage())), e.retryable());
        } catch (RuntimeException e) {
            log.error("Error inesperado en la búsqueda {}", search.id(), e);
            handleFailure(search, new Failure("INTERNAL_ERROR", "Error inesperado al buscar."), true);
        }
    }

    private void handleFailure(JobSearch search, Failure failure, boolean retryable) {
        Instant now = clock.instant();
        if (retryable && retries.allowsAfter(search.attempts())) {
            Instant next = now.plus(retries.delayBefore(search.attempts() + 1));
            log.info("Búsqueda {} falló con {} (intento {}/{}); se reintenta a las {}", search.id(), failure.code(),
                    search.attempts(), retries.maxAttempts(), next);
            outcomes.retry(search, failure, next);
        } else {
            log.info("Búsqueda {} falló con {} (definitivo)", search.id(), failure.code());
            outcomes.failed(search, failure, now);
        }
    }

    private static String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() <= MAX_MESSAGE ? message : message.substring(0, MAX_MESSAGE);
    }
}

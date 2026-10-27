package com.aplicafacil.jobs.application;

import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aplicafacil.jobs.application.port.out.JobSearchCommands;
import com.aplicafacil.jobs.application.port.out.JobSearchStates;
import com.aplicafacil.jobs.domain.JobSearch;

/** Cierra un intento. Un reintento cambia el estado y encola el siguiente intento en la misma transacción (outbox). */
@Service
public class JobSearchOutcomes {

    private final JobSearchStates states;
    private final JobSearchCommands commands;

    public JobSearchOutcomes(JobSearchStates states, JobSearchCommands commands) {
        this.states = states;
        this.commands = commands;
    }

    @Transactional
    public void completed(JobSearch search, int found, int created, Instant now) {
        states.complete(search.id(), found, created, now);
    }

    @Transactional
    public void retry(JobSearch search, Failure failure, Instant nextAttemptAt) {
        if (states.retryLater(search.id(), failure.code(), failure.message(), nextAttemptAt)) {
            commands.scheduleRetry(search.id(), search.userId(), search.attempts() + 1);
        }
    }

    @Transactional
    public void failed(JobSearch search, Failure failure, Instant now) {
        states.fail(search.id(), failure.code(), failure.message(), now);
    }

    /** Código estable y mensaje ya acotado para mostrar. */
    public record Failure(String code, String message) {
    }
}

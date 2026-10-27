package com.aplicafacil.jobs.infrastructure.messaging;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.aplicafacil.jobs.application.port.out.JobSearchCommands;
import com.aplicafacil.jobs.infrastructure.persistence.SpringDataOutboxEventRepository;
import com.aplicafacil.jobs.infrastructure.persistence.entity.OutboxEventEntity;

import tools.jackson.databind.json.JsonMapper;

/**
 * {@link JobSearchCommands} sobre el outbox: el comando {@code jobs.search.requested}
 * (contracts/schemas/job-search-requested-command.schema.json) se guarda en la transacción en curso y
 * {@link OutboxPublisher} lo publica. Un reintento va por {@code jobs.search.retry.{n}} a su cola de espera.
 */
@Component
public class OutboxJobSearchCommands implements JobSearchCommands {

    static final int SCHEMA_VERSION = 1;

    private final SpringDataOutboxEventRepository outbox;
    private final JsonMapper json;
    private final Clock clock;

    public OutboxJobSearchCommands(SpringDataOutboxEventRepository outbox, JsonMapper json, Clock clock) {
        this.outbox = outbox;
        this.json = json;
        this.clock = clock;
    }

    @Override
    public void requestFirstRun(UUID searchId, UUID userId) {
        save(JobSearchMessaging.REQUESTED, searchId, userId, 1);
    }

    @Override
    public void scheduleRetry(UUID searchId, UUID userId, int attempt) {
        save(JobSearchMessaging.RETRY_PREFIX + attempt, searchId, userId, attempt);
    }

    private void save(String routingKey, UUID searchId, UUID userId, int attempt) {
        Instant now = clock.instant();
        UUID commandId = UUID.randomUUID();
        JobSearchRequested command = new JobSearchRequested(SCHEMA_VERSION, commandId, searchId, userId, attempt,
                now.toString());
        outbox.save(OutboxEventEntity.builder()
                .id(commandId)
                .aggregateType("job_search")
                .aggregateId(searchId)
                .exchange(JobSearchMessaging.COMMANDS_EXCHANGE)
                .eventType(routingKey)
                .payload(json.writeValueAsString(command))
                .createdAt(now)
                .build());
    }

    /** {@code jobs.search.requested}: solo ids; los criterios están en la BD y la sesión nunca viaja por la cola. */
    record JobSearchRequested(int schemaVersion, UUID commandId, UUID searchId, UUID userId, int attempt,
                              String requestedAt) {
    }
}

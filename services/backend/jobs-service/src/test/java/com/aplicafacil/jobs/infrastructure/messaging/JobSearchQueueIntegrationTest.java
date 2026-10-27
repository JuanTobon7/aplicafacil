package com.aplicafacil.jobs.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

import com.aplicafacil.jobs.application.JobPortalGateways;
import com.aplicafacil.jobs.application.JobSearchService;
import com.aplicafacil.jobs.application.JobSearchView;
import com.aplicafacil.jobs.application.port.out.JobPortalException;
import com.aplicafacil.jobs.application.port.out.JobPortalGateway;
import com.aplicafacil.jobs.domain.JobCard;
import com.aplicafacil.jobs.domain.JobPlatform;
import com.aplicafacil.jobs.domain.JobSearchStatus;
import com.aplicafacil.jobs.domain.SearchCriteria;
import com.aplicafacil.jobs.infrastructure.persistence.SpringDataJobSearchRepository;
import com.aplicafacil.jobs.infrastructure.persistence.entity.JobSearchEntity;

/**
 * La búsqueda de punta a punta por RabbitMQ real: pedirla deja el comando en el outbox, se publica, el consumidor la
 * procesa, un fallo transitorio espera en la cola de espera y vuelve, y un mensaje ilegible termina en la DLQ.
 */
@SpringBootTest(properties = {
        "spring.ai.model.chat=none",
        "spring.ai.openai.api-key=unused-in-tests",
        "aplicafacil.jobs.worker.retry-backoff=1s",
        "aplicafacil.jobs.worker.max-attempts=2"})
@Testcontainers
class JobSearchQueueIntegrationTest {

    private static final Duration WAIT = Duration.ofSeconds(30);

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:0.8.6-pg18").asCompatibleSubstituteFor("postgres"));

    @Container
    @ServiceConnection
    static RabbitMQContainer rabbit = new RabbitMQContainer(DockerImageName.parse("rabbitmq:4.3-management"));

    @Autowired
    JobSearchService service;

    @Autowired
    SpringDataJobSearchRepository searches;

    @Autowired
    RabbitTemplate rabbitTemplate;

    @Autowired
    AmqpAdmin admin;

    /** El portal se simula: aquí se prueba la cola, no el scraper. */
    @MockitoBean
    JobPortalGateways gateways;

    /** Los endpoints no se llaman; el decoder real (JWKS de identity-service) no se usa. */
    @MockitoBean
    JwtDecoder jwtDecoder;

    private final AtomicInteger calls = new AtomicInteger();

    @BeforeEach
    void portal() {
        when(gateways.supported()).thenReturn(Set.of(JobPlatform.COMPUTRABAJO));
        admin.purgeQueue(JobSearchMessaging.QUEUE + ".dlq");
    }

    @Test
    void aRequestedSearchIsPublishedConsumedAndCompleted() {
        when(gateways.get(any())).thenReturn(portal(attempt -> List.of(card("1"), card("2"))));

        JobSearchView view = service.request(UUID.randomUUID(), JobPlatform.COMPUTRABAJO, criteria());

        JobSearchEntity done = awaitStatus(view.id(), JobSearchStatus.COMPLETED);
        assertThat(done.getAttempts()).isEqualTo(1);
        assertThat(done.getFoundCount()).isEqualTo(2);
    }

    @Test
    void aTransientFailureWaitsInItsWaitQueueAndTheNextAttemptCompletes() {
        when(gateways.get(any())).thenReturn(portal(attempt -> {
            if (attempt == 1) {
                throw new JobPortalException("PORTAL_UNAVAILABLE", "caído", true);
            }
            return List.of(card("3"));
        }));

        JobSearchView view = service.request(UUID.randomUUID(), JobPlatform.COMPUTRABAJO, criteria());

        JobSearchEntity done = awaitStatus(view.id(), JobSearchStatus.COMPLETED);
        assertThat(done.getAttempts()).isEqualTo(2);
        assertThat(done.getErrorCode()).isNull();
    }

    @Test
    void aRepeatedMessageForAFinishedSearchIsIgnored() {
        when(gateways.get(any())).thenReturn(portal(attempt -> List.of(card("4"))));
        JobSearchView view = service.request(UUID.randomUUID(), JobPlatform.COMPUTRABAJO, criteria());
        awaitStatus(view.id(), JobSearchStatus.COMPLETED);
        int before = calls.get();

        publish("""
                {"schemaVersion":1,"commandId":"%s","searchId":"%s","userId":"%s","attempt":1,"requestedAt":"%s"}
                """.formatted(UUID.randomUUID(), view.id(), UUID.randomUUID(), Instant.now()));

        await(() -> admin.getQueueInfo(JobSearchMessaging.QUEUE).getMessageCount() == 0);
        assertThat(calls.get()).isEqualTo(before);
        assertThat(searches.findById(view.id()).orElseThrow().getAttempts()).isEqualTo(1);
    }

    @Test
    void anAiSearchPlanQueuesEachSearchOnceEvenIfTheEventArrivesTwice() {
        when(gateways.get(any())).thenReturn(portal(attempt -> List.of(card("5"))));
        UUID planId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String event = """
                {"schemaVersion":1,"eventId":"%s","planId":"%s","userId":"%s",
                 "searches":["desarrollador java","backend java"],"remoteOnly":true,"postedWithinDays":7,
                 "location":null,"occurredAt":"%s"}""".formatted(UUID.randomUUID(), planId, userId, Instant.now());

        rabbitTemplate.send("aplicafacil.events", SearchPlanListener.READY, json(event));
        rabbitTemplate.send("aplicafacil.events", SearchPlanListener.READY, json(event));

        await(() -> searches.findAll().stream().filter(s -> planId.equals(s.getPlanId()))
                .filter(s -> s.getStatus() == JobSearchStatus.COMPLETED).count() == 2);
        await(() -> admin.getQueueInfo(SearchPlanListener.QUEUE).getMessageCount() == 0);
        List<JobSearchEntity> launched = searches.findAll().stream().filter(s -> planId.equals(s.getPlanId())).toList();
        assertThat(launched).extracting(JobSearchEntity::getKeywords)
                .containsExactlyInAnyOrder("desarrollador java", "backend java");
        assertThat(launched).allSatisfy(s -> {
            assertThat(s.isRemote()).isTrue();
            assertThat(s.getPostedWithinDays()).isEqualTo(7);
            assertThat(s.getUserId()).isEqualTo(userId);
        });
    }

    @Test
    void anUnreadableMessageGoesToTheDeadLetterQueue() {
        publish("{\"searchId\":\"no-es-un-uuid\"}");

        await(() -> admin.getQueueInfo(JobSearchMessaging.QUEUE + ".dlq").getMessageCount() == 1);
    }

    private JobPortalGateway portal(Attempt behavior) {
        AtomicInteger attempts = new AtomicInteger();
        return new JobPortalGateway() {
            @Override
            public JobPlatform platform() {
                return JobPlatform.COMPUTRABAJO;
            }

            @Override
            public List<JobCard> search(UUID userId, SearchCriteria criteria) {
                calls.incrementAndGet();
                return behavior.run(attempts.incrementAndGet());
            }
        };
    }

    private void publish(String body) {
        rabbitTemplate.send(JobSearchMessaging.COMMANDS_EXCHANGE, JobSearchMessaging.REQUESTED, json(body));
    }

    private static Message json(String body) {
        return MessageBuilder.withBody(body.getBytes(StandardCharsets.UTF_8))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON).build();
    }

    private JobSearchEntity awaitStatus(UUID id, JobSearchStatus status) {
        await(() -> searches.findById(id).map(s -> s.getStatus() == status).orElse(false));
        return searches.findById(id).orElseThrow();
    }

    private static void await(BooleanSupplier condition) {
        long deadline = System.nanoTime() + WAIT.toNanos();
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("No se cumplió en " + WAIT);
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError(e);
            }
        }
    }

    private static SearchCriteria criteria() {
        return new SearchCriteria("java", null, 10, null);
    }

    private static JobCard card(String id) {
        return new JobCard(id + "-" + UUID.randomUUID(), "https://co.computrabajo.com/ofertas/" + id, "Oferta " + id,
                "Acme", "Bogotá", null, null, false);
    }

    @FunctionalInterface
    private interface Attempt {
        List<JobCard> run(int attempt);
    }
}

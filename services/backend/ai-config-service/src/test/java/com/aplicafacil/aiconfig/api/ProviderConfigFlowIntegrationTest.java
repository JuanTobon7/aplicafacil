package com.aplicafacil.aiconfig.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import java.util.function.Predicate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

import com.aplicafacil.aiconfig.infrastructure.messaging.MessagingConfig;
import com.jayway.jsonpath.JsonPath;

/**
 * De punta a punta dentro de ai-config-service con Postgres y RabbitMQ reales: API del front, outbox hacia
 * RabbitMQ, eventos del mcp-gateway (simulados publicando en el exchange) y la API interna de credenciales.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ProviderConfigFlowIntegrationTest {

    private static final String API_KEY = "sk-test-SECRET-0123456789abcd";
    private static final String CONFIG_EVENTS_PROBE = "test.ai-config-events";
    private static final String COMMANDS_PROBE = "test.ai-commands";

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:0.8.6-pg18").asCompatibleSubstituteFor("postgres"));

    @Container
    @ServiceConnection
    static RabbitMQContainer rabbit = new RabbitMQContainer(DockerImageName.parse("rabbitmq:4.3-management"));

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        registry.add("aplicafacil.session-encryption.key", () -> Base64.getEncoder().encodeToString(key));
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    RabbitTemplate rabbitTemplate;

    @Autowired
    AmqpAdmin admin;

    /** Los tokens se simulan con jwt(); el decoder real (JWKS de identity-service) no se usa. */
    @MockitoBean
    JwtDecoder jwtDecoder;

    @BeforeEach
    void probes() {
        // RabbitMQ 4 no admite colas transitorias no exclusivas; sin auto-delete, porque cada receive()
        // crea y cancela un consumidor
        Queue events = new Queue(CONFIG_EVENTS_PROBE, false, true, false);
        Queue commands = new Queue(COMMANDS_PROBE, false, true, false);
        admin.declareQueue(events);
        admin.declareQueue(commands);
        admin.declareBinding(BindingBuilder.bind(events).to(new TopicExchange(MessagingConfig.EVENTS_EXCHANGE))
                .with("ai.provider-config.#"));
        admin.declareBinding(BindingBuilder.bind(commands).to(new TopicExchange(MessagingConfig.COMMANDS_EXCHANGE))
                .with("ai.#"));
        admin.purgeQueue(CONFIG_EVENTS_PROBE);
        admin.purgeQueue(COMMANDS_PROBE);
    }

    @Test
    void lifecyclePublishesVersionedEventsAndNeverExposesTheKey() throws Exception {
        UUID userId = UUID.randomUUID();
        String created = mvc.perform(post("/ai/provider-configs").with(user(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"providerType":"OPENAI","displayName":"Mi OpenAI","apiKey":"%s",
                                 "model":"gpt-4.1-mini","parameters":{"temperature":0.2,"maxTokens":256}}
                                """.formatted(API_KEY)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.credentialHint").value("••••abcd"))
                .andReturn().getResponse().getContentAsString();
        assertThat(created).doesNotContain("SECRET").doesNotContain("apiKey");
        String configId = JsonPath.read(created, "$.id");

        Message createdEvent = receive(CONFIG_EVENTS_PROBE);
        assertThat(createdEvent.getMessageProperties().getReceivedRoutingKey()).isEqualTo("ai.provider-config.created");
        assertThat(createdEvent.getMessageProperties().getMessageId()).isNotBlank();
        assertThat(createdEvent.getMessageProperties().getCorrelationId()).isNotBlank();
        String createdBody = body(createdEvent);
        assertThat(createdBody).doesNotContain("SECRET");
        assertThat((Integer) JsonPath.read(createdBody, "$.version")).isEqualTo(1);
        assertThat((String) JsonPath.read(createdBody, "$.userId")).isEqualTo(userId.toString());

        // Solo el mcp-gateway, y solo para el dueño, recibe la key descifrada
        mvc.perform(get("/internal/provider-configs/{id}", configId).param("userId", userId.toString())
                        .with(service("mcp-gateway")))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.apiKey").value(API_KEY))
                .andExpect(jsonPath("$.version").value(1));
        mvc.perform(get("/internal/provider-configs/{id}", configId).param("userId", userId.toString())
                        .with(service("linkedin-service")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/internal/provider-configs/{id}", configId).param("userId", UUID.randomUUID().toString())
                        .with(service("mcp-gateway")))
                .andExpect(status().isNotFound());
        mvc.perform(get("/internal/provider-configs/{id}", configId).param("userId", userId.toString())
                        .with(user(userId)))
                .andExpect(status().isForbidden());

        // Rotación: nueva versión, nueva key, la anterior deja de existir
        String newKey = "sk-test-ROTATED-9876543210wxyz";
        mvc.perform(put("/ai/provider-configs/{id}/credential", configId).with(user(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"apiKey\":\"%s\",\"expectedVersion\":1}".formatted(newKey)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.credentialHint").value("••••wxyz"));
        Message rotated = receive(CONFIG_EVENTS_PROBE);
        assertThat(rotated.getMessageProperties().getReceivedRoutingKey())
                .isEqualTo("ai.provider-config.credential-rotated");
        assertThat(body(rotated)).doesNotContain("ROTATED");
        mvc.perform(get("/internal/provider-configs/{id}", configId).param("userId", userId.toString())
                        .with(service("mcp-gateway")))
                .andExpect(jsonPath("$.apiKey").value(newKey));

        // Edición con una versión vieja: conflicto
        mvc.perform(put("/ai/provider-configs/{id}", configId).with(user(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Otro\",\"expectedVersion\":1}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));

        // Desactivar y borrar
        mvc.perform(put("/ai/provider-configs/{id}/enabled", configId).with(user(userId))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}"))
                .andExpect(jsonPath("$.status").value("DISABLED"))
                .andExpect(jsonPath("$.version").value(3));
        assertThat(receive(CONFIG_EVENTS_PROBE).getMessageProperties().getReceivedRoutingKey())
                .isEqualTo("ai.provider-config.disabled");
        mvc.perform(delete("/ai/provider-configs/{id}", configId).with(user(userId)))
                .andExpect(status().isNoContent());
        Message deleted = receive(CONFIG_EVENTS_PROBE);
        assertThat(deleted.getMessageProperties().getReceivedRoutingKey()).isEqualTo("ai.provider-config.deleted");
        assertThat((Integer) JsonPath.read(body(deleted), "$.version")).isEqualTo(4);
        mvc.perform(get("/ai/provider-configs/{id}", configId).with(user(userId)))
                .andExpect(status().isNotFound());
    }

    @Test
    void anotherUserCannotReadOrChangeAConfiguration() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID intruder = UUID.randomUUID();
        String configId = createConfig(owner, "Privada");

        mvc.perform(get("/ai/provider-configs/{id}", configId).with(user(intruder)))
                .andExpect(status().isNotFound());
        mvc.perform(put("/ai/provider-configs/{id}/enabled", configId).with(user(intruder))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}"))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/ai/provider-configs/{id}", configId).with(user(intruder)))
                .andExpect(status().isNotFound());
        mvc.perform(post("/ai/requests").with(user(intruder)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"configId\":\"%s\",\"prompt\":\"hola\"}".formatted(configId)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/ai/provider-configs").with(user(intruder)))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void rejectsInvalidInput() throws Exception {
        UUID userId = UUID.randomUUID();
        mvc.perform(post("/ai/provider-configs").with(user(userId)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"providerType\":\"NOPE\",\"displayName\":\"x\",\"apiKey\":\"%s\"}".formatted(API_KEY)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UNKNOWN_PROVIDER_TYPE"));
        mvc.perform(post("/ai/provider-configs").with(user(userId)).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"providerType":"OPENROUTER","displayName":"x","apiKey":"%s","baseUrl":"https://evil.example"}
                                """.formatted(API_KEY)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BASE_URL_NOT_ALLOWED"));
        mvc.perform(post("/ai/provider-configs").with(user(userId)).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"providerType":"OPENAI","displayName":"x","apiKey":"%s","baseUrl":"http://10.0.0.5:11434/v1"}
                                """.formatted(API_KEY)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_BASE_URL"));
        mvc.perform(post("/ai/provider-configs").with(user(userId)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"providerType\":\"OPENAI\",\"displayName\":\"x\",\"apiKey\":\"con espacios no\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/ai/provider-configs").with(user(userId)).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"providerType":"OPENAI","displayName":"x","apiKey":"%s","parameters":{"temperature":3}}
                                """.formatted(API_KEY)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void appliesStatusOnlyForTheCurrentVersion() throws Exception {
        UUID userId = UUID.randomUUID();
        String configId = createConfig(userId, "Estados");

        publishStatus("ai.provider.initialization-started", configId, userId, 1, "INITIALIZING", null, UUID.randomUUID());
        awaitConfig(userId, configId, "$.status", "INITIALIZING"::equals);

        publishStatus("ai.provider.initialization-succeeded", configId, userId, 1, "ACTIVE",
                ",\"models\":[\"gpt-4.1-mini\",\"gpt-4.1\"]", UUID.randomUUID());
        awaitConfig(userId, configId, "$.status", "ACTIVE"::equals);

        // Un INITIALIZING repetido (otra instancia) no hace retroceder el estado
        publishStatus("ai.provider.initialization-started", configId, userId, 1, "INITIALIZING", null, UUID.randomUUID());
        // Barrera: los mensajes de una cola se procesan en orden
        mvc.perform(put("/ai/provider-configs/{id}", configId).with(user(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Estados\",\"model\":\"gpt-4.1\",\"expectedVersion\":1}"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.version").value(2));

        // Un FAILED atrasado (v1) se ignora; el ACTIVE de la v2 se aplica
        publishStatus("ai.provider.initialization-failed", configId, userId, 1, "FAILED",
                ",\"errorCode\":\"AUTHENTICATION_FAILED\",\"errorMessage\":\"viejo\"", UUID.randomUUID());
        publishStatus("ai.provider.initialization-succeeded", configId, userId, 2, "ACTIVE", null, UUID.randomUUID());
        awaitConfig(userId, configId, "$.status", "ACTIVE"::equals);
        mvc.perform(get("/ai/provider-configs/{id}", configId).with(user(userId)))
                .andExpect(jsonPath("$.statusCode").doesNotExist())
                .andExpect(jsonPath("$.availableModels.length()").value(2));
    }

    @Test
    void connectionTestIsCorrelatedByCorrelationId() throws Exception {
        UUID userId = UUID.randomUUID();
        String configId = createConfig(userId, "Prueba");
        receive(CONFIG_EVENTS_PROBE);

        String requested = mvc.perform(post("/ai/provider-configs/{id}/test", configId).with(user(userId)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.lastTest.success").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        assertThat((Object) JsonPath.read(requested, "$.lastTest")).isNotNull();

        Message command = receive(COMMANDS_PROBE);
        assertThat(command.getMessageProperties().getReceivedRoutingKey()).isEqualTo("ai.provider.test");
        UUID correlationId = UUID.fromString(JsonPath.read(body(command), "$.correlationId"));

        // Un resultado de otra prueba no la cierra
        publishStatus("ai.provider.test-completed", configId, userId, 1, "FAILED", null, UUID.randomUUID());
        publishStatus("ai.provider.test-completed", configId, userId, 1, "ACTIVE", ",\"latencyMs\":42", correlationId);
        awaitConfig(userId, configId, "$.lastTest.success", Boolean.TRUE::equals);
        mvc.perform(get("/ai/provider-configs/{id}", configId).with(user(userId)))
                .andExpect(jsonPath("$.lastTest.latencyMs").value(42));
    }

    @Test
    void asyncAiRequestIsCorrelatedAndIdempotent() throws Exception {
        UUID userId = UUID.randomUUID();
        String configId = createConfig(userId, "Solicitudes");

        String submitted = mvc.perform(post("/ai/requests").with(user(userId)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"configId\":\"%s\",\"prompt\":\"Di hola\",\"systemPrompt\":\"Breve\"}"
                                .formatted(configId)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn().getResponse().getContentAsString();
        String requestId = JsonPath.read(submitted, "$.id");

        Message command = receive(COMMANDS_PROBE);
        String commandBody = body(command);
        assertThat(command.getMessageProperties().getReceivedRoutingKey()).isEqualTo("ai.request.submit");
        assertThat((String) JsonPath.read(commandBody, "$.requestId")).isEqualTo(requestId);
        assertThat((String) JsonPath.read(commandBody, "$.messages[0].role")).isEqualTo("system");
        assertThat((String) JsonPath.read(commandBody, "$.messages[1].content")).isEqualTo("Di hola");
        assertThat((Integer) JsonPath.read(commandBody, "$.parameters.maxTokens")).isEqualTo(256);
        assertThat(commandBody).doesNotContain("SECRET");
        String correlationId = JsonPath.read(commandBody, "$.correlationId");

        publish("ai.request.completed", """
                {"schemaVersion":1,"eventId":"%s","eventType":"ai.request.completed","requestId":"%s","configId":"%s",
                 "userId":"%s","correlationId":"%s","output":{"text":"¡Hola!","model":"gpt-4.1-mini",
                 "finishReason":"stop","usage":{"inputTokens":5,"outputTokens":3,"totalTokens":8}},
                 "latencyMs":120,"occurredAt":"%s"}
                """.formatted(UUID.randomUUID(), requestId, configId, userId, correlationId, Instant.now()));
        // Un duplicado que dice lo contrario no cambia el resultado ya cerrado
        publish("ai.request.failed", """
                {"schemaVersion":1,"eventId":"%s","eventType":"ai.request.failed","requestId":"%s","configId":"%s",
                 "userId":"%s","correlationId":"%s","error":{"code":"X","message":"dup","retryable":false},
                 "occurredAt":"%s"}
                """.formatted(UUID.randomUUID(), requestId, configId, userId, correlationId, Instant.now()));

        await(() -> mvc.perform(get("/ai/requests/{id}", requestId).with(user(userId)))
                .andReturn().getResponse().getContentAsString(), "$.status", "COMPLETED"::equals);
        Thread.sleep(500);
        mvc.perform(get("/ai/requests/{id}", requestId).with(user(userId)))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.outputText").value("¡Hola!"))
                .andExpect(jsonPath("$.outputTokens").value(3));
    }

    @Test
    void messageThatBreaksTheContractGoesToTheDlq() throws Exception {
        publish("ai.provider.initialization-succeeded", "{\"no\":\"cumple\"}");
        Message dead = rabbitTemplate.receive(MessagingConfig.PROVIDER_STATUS_QUEUE + ".dlq", 10_000);
        assertThat(dead).isNotNull();
        assertThat(body(dead)).contains("cumple");
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private String createConfig(UUID userId, String name) throws Exception {
        String created = mvc.perform(post("/ai/provider-configs").with(user(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"providerType":"OPENAI","displayName":"%s","apiKey":"%s","parameters":{"maxTokens":256}}
                                """.formatted(name, API_KEY)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(created, "$.id");
    }

    private void publishStatus(String eventType, String configId, UUID userId, long version, String status,
                               String extra, UUID correlationId) {
        publish(eventType, """
                {"schemaVersion":1,"eventId":"%s","eventType":"%s","configId":"%s","userId":"%s","configVersion":%d,
                 "status":"%s","correlationId":"%s","occurredAt":"%s","instanceId":"test"%s}
                """.formatted(UUID.randomUUID(), eventType, configId, userId, version, status, correlationId,
                Instant.now(), extra == null ? "" : extra));
    }

    private void publish(String routingKey, String json) {
        MessageProperties props = new MessageProperties();
        props.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        rabbitTemplate.send(MessagingConfig.EVENTS_EXCHANGE, routingKey,
                MessageBuilder.withBody(json.getBytes(StandardCharsets.UTF_8)).andProperties(props).build());
    }

    private Message receive(String queue) {
        Message message = rabbitTemplate.receive(queue, 10_000);
        assertThat(message).as("mensaje en " + queue).isNotNull();
        return message;
    }

    private void awaitConfig(UUID userId, String configId, String path, Predicate<Object> condition) throws Exception {
        await(() -> mvc.perform(get("/ai/provider-configs/{id}", configId).with(user(userId)))
                .andReturn().getResponse().getContentAsString(), path, condition);
    }

    private static void await(ThrowingSupplier body, String path, Predicate<Object> condition) throws Exception {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(10));
        Object last = null;
        while (Instant.now().isBefore(deadline)) {
            try {
                last = JsonPath.read(body.get(), path);
            } catch (com.jayway.jsonpath.PathNotFoundException e) {
                last = null;
            }
            if (condition.test(last)) {
                return;
            }
            Thread.sleep(100);
        }
        throw new AssertionError(path + " no cumplió la condición; último valor: " + last);
    }

    private static String body(Message message) {
        return new String(message.getBody(), StandardCharsets.UTF_8);
    }

    private static RequestPostProcessor user(UUID userId) {
        return jwt().jwt(j -> j.subject(userId.toString())).authorities(new SimpleGrantedAuthority("ROLE_USER"));
    }

    private static RequestPostProcessor service(String clientId) {
        return jwt().jwt(j -> j.subject(clientId)).authorities(new SimpleGrantedAuthority("ROLE_SERVICE"));
    }

    @FunctionalInterface
    private interface ThrowingSupplier {
        String get() throws Exception;
    }
}

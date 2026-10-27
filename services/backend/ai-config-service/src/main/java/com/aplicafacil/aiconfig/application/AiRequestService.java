package com.aplicafacil.aiconfig.application;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aplicafacil.aiconfig.application.exception.AiConfigException;
import com.aplicafacil.aiconfig.application.port.out.AiEventOutbox;
import com.aplicafacil.aiconfig.application.port.out.AiEventOutbox.ChatMessage;
import com.aplicafacil.aiconfig.application.port.out.AiRequestRepository;
import com.aplicafacil.aiconfig.application.port.out.ProviderConfigRepository;
import com.aplicafacil.aiconfig.domain.AiRequest;
import com.aplicafacil.aiconfig.domain.AiRequestStatus;
import com.aplicafacil.aiconfig.domain.ModelParameters;
import com.aplicafacil.aiconfig.domain.ProviderConfig;

/**
 * Solicitudes de IA asíncronas desde el front: el backend valida y autoriza, publica {@code ai.request.submit}
 * con un correlationId y devuelve 202. El resultado ({@code ai.request.completed|failed}) se correlaciona por
 * {@code requestId}; si no llega antes de {@code deadline}, la solicitud se cierra como {@code TIMED_OUT} y un
 * resultado tardío se ignora.
 */
@Service
public class AiRequestService {

    private static final Logger log = LoggerFactory.getLogger(AiRequestService.class);
    private static final int RECENT_LIMIT = 20;

    private final AiRequestRepository requests;
    private final ProviderConfigRepository configs;
    private final AiEventOutbox outbox;
    private final AiPolicy policy;
    private final Clock clock;

    public AiRequestService(AiRequestRepository requests, ProviderConfigRepository configs, AiEventOutbox outbox,
                            AiPolicy policy, Clock clock) {
        this.requests = requests;
        this.configs = configs;
        this.outbox = outbox;
        this.policy = policy;
        this.clock = clock;
    }

    @Transactional
    public AiRequestView submit(UUID userId, NewRequest request) {
        ProviderConfig config = configs.findOwned(request.configId(), userId)
                .orElseThrow(AiConfigException::configNotFound);
        if (!config.enabled()) {
            throw AiConfigException.configDisabled();
        }
        Instant now = clock.instant();
        AiRequest submitted = AiRequest.submit(userId, config.id(), now, now.plus(policy.requestTimeout()));
        requests.save(submitted);
        ModelParameters parameters = request.parameters() == null
                ? config.snapshot().settings().parameters() : request.parameters();
        outbox.requestSubmitted(submitted, request.messages(), parameters, request.model());
        return AiRequestView.of(submitted);
    }

    @Transactional(readOnly = true)
    public AiRequestView get(UUID userId, UUID requestId) {
        return requests.findOwned(requestId, userId).map(AiRequestView::of)
                .orElseThrow(AiConfigException::requestNotFound);
    }

    @Transactional(readOnly = true)
    public List<AiRequestView> recent(UUID userId) {
        return requests.recent(userId, RECENT_LIMIT).stream().map(AiRequestView::of).toList();
    }

    /** @return {@code true} si cerró la solicitud; {@code false} si era repetido, tardío o ajeno */
    @Transactional
    public boolean applyResult(ResultEvent event) {
        AiRequest request = requests.findForUpdate(event.requestId()).orElse(null);
        if (request == null || request.isClosed()) {
            return false;
        }
        if (!request.belongsTo(event.userId(), event.configId())) {
            log.warn("Resultado de IA {} con userId/configId que no corresponden: se ignora", event.requestId());
            return false;
        }
        Instant now = clock.instant();
        if (event.completed()) {
            request.complete(new AiRequest.Output(event.text(), event.model(), event.finishReason(),
                    event.inputTokens(), event.outputTokens()), event.latencyMs(), now);
        } else {
            request.fail(event.errorCode(), event.errorMessage(), event.latencyMs(), now);
        }
        requests.save(request);
        return true;
    }

    @Scheduled(fixedDelay = 10_000, initialDelay = 10_000)
    @Transactional
    public void expire() {
        int expired = requests.expire(clock.instant());
        if (expired > 0) {
            log.info("{} solicitudes de IA vencidas sin respuesta del mcp-gateway", expired);
        }
    }

    public record NewRequest(UUID configId, String prompt, String systemPrompt, String model,
                             ModelParameters parameters) {

        /** El system prompt (si hay) y el prompt, ya limpios. */
        List<ChatMessage> messages() {
            List<ChatMessage> chat = new ArrayList<>();
            if (systemPrompt != null && !systemPrompt.isBlank()) {
                chat.add(new ChatMessage("system", systemPrompt.strip()));
            }
            chat.add(new ChatMessage("user", prompt.strip()));
            return chat;
        }
    }

    /** {@code ai.request.completed|failed} ya leído (contracts/schemas/ai-request-result-event.schema.json). */
    public record ResultEvent(UUID requestId, UUID configId, UUID userId, boolean completed, String text,
                              String model, String finishReason, Integer inputTokens, Integer outputTokens,
                              String errorCode, String errorMessage, Long latencyMs) {
    }

    public record AiRequestView(UUID id, UUID configId, AiRequestStatus status, String outputText, String model,
                                String finishReason, Integer inputTokens, Integer outputTokens, String errorCode,
                                String errorMessage, Integer latencyMs, Instant createdAt, Instant deadline,
                                Instant completedAt) {

        static AiRequestView of(AiRequest request) {
            AiRequest.Snapshot r = request.snapshot();
            AiRequest.Output out = r.output() == null ? new AiRequest.Output(null, null, null, null, null) : r.output();
            AiRequest.Failure err = r.failure() == null ? new AiRequest.Failure(null, null) : r.failure();
            return new AiRequestView(r.id(), r.configId(), r.status(), out.text(), out.model(), out.finishReason(),
                    out.inputTokens(), out.outputTokens(), err.code(), err.message(), r.latencyMs(), r.createdAt(),
                    r.deadline(), r.completedAt());
        }
    }
}

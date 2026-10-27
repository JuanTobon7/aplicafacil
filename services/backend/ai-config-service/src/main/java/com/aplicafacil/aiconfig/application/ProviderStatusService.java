package com.aplicafacil.aiconfig.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aplicafacil.aiconfig.application.port.out.PendingTests;
import com.aplicafacil.aiconfig.application.port.out.ProviderConfigRepository;
import com.aplicafacil.aiconfig.domain.ProviderConfig;
import com.aplicafacil.aiconfig.domain.ProviderConfigStatus;

/**
 * Aplica los estados que informa el mcp-gateway ({@code ai.provider.*}). Es idempotente y tolera mensajes
 * repetidos, desordenados o de varias instancias; qué se acepta lo decide {@link ProviderConfig}.
 */
@Service
public class ProviderStatusService {

    public static final String INITIALIZATION_STARTED = "ai.provider.initialization-started";
    public static final String INITIALIZATION_SUCCEEDED = "ai.provider.initialization-succeeded";
    public static final String INITIALIZATION_FAILED = "ai.provider.initialization-failed";
    public static final String TEST_COMPLETED = "ai.provider.test-completed";

    private static final Logger log = LoggerFactory.getLogger(ProviderStatusService.class);

    private final ProviderConfigRepository configs;
    private final PendingTests pendingTests;
    private final AiPolicy policy;
    private final Clock clock;

    public ProviderStatusService(ProviderConfigRepository configs, PendingTests pendingTests, AiPolicy policy,
                                 Clock clock) {
        this.configs = configs;
        this.pendingTests = pendingTests;
        this.policy = policy;
        this.clock = clock;
    }

    /** @return {@code true} si el evento cambió algo */
    @Transactional
    public boolean apply(StatusEvent event) {
        ProviderConfig config = configs.findForUpdate(event.configId()).orElse(null);
        if (config == null) {
            log.debug("Estado de una configuración que ya no existe ({}): se ignora", event.configId());
            return false;
        }
        if (!config.userId().equals(event.userId())) {
            log.warn("Estado con userId que no corresponde a la configuración {}: se ignora", event.configId());
            return false;
        }
        Instant now = clock.instant();
        boolean changed = TEST_COMPLETED.equals(event.eventType())
                ? config.completeTest(event.correlationId(), event.testOutcome(), now)
                : config.applyStatus(event.statusReport(), now);
        if (changed) {
            configs.save(config);
        } else {
            log.debug("Estado de la versión {} de {} ignorado (vigente: {})", event.configVersion(), config.id(),
                    config.version());
        }
        return changed;
    }

    /** Pruebas sin respuesta (mcp-gateway apagado): se cierran como fallidas para no quedar "en curso" siempre. */
    @Scheduled(fixedDelay = 10_000, initialDelay = 10_000)
    @Transactional
    public void expireTests() {
        for (ProviderConfig config : pendingTests.startedBefore(clock.instant().minus(policy.requestTimeout()))) {
            config.expireTest();
            configs.save(config);
        }
    }

    /** {@code ai.provider.*} (contracts/schemas/ai-provider-status-event.schema.json). */
    public record StatusEvent(String eventType, UUID configId, UUID userId, long configVersion,
                              ProviderConfigStatus status, String errorCode, String errorMessage, List<String> models,
                              Long latencyMs, UUID correlationId) {

        ProviderConfig.StatusReport statusReport() {
            return new ProviderConfig.StatusReport(configVersion, status, errorCode, errorMessage, models);
        }

        ProviderConfig.TestOutcome testOutcome() {
            return new ProviderConfig.TestOutcome(status == ProviderConfigStatus.ACTIVE,
                    latencyMs == null ? null : Math.toIntExact(latencyMs), errorCode, errorMessage);
        }
    }
}

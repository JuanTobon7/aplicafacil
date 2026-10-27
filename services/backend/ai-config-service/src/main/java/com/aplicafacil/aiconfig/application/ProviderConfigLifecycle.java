package com.aplicafacil.aiconfig.application;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aplicafacil.aiconfig.application.exception.AiConfigException;
import com.aplicafacil.aiconfig.application.port.out.AiEventOutbox;
import com.aplicafacil.aiconfig.application.port.out.AiEventOutbox.ConfigChange;
import com.aplicafacil.aiconfig.application.port.out.CredentialVault;
import com.aplicafacil.aiconfig.application.port.out.CredentialVault.StoredCredential;
import com.aplicafacil.aiconfig.application.port.out.ProviderConfigRepository;
import com.aplicafacil.aiconfig.domain.ProviderConfig;

/**
 * Lo que le pasa a una configuración ya creada fuera de editar sus datos: rotar la key, probar la conexión y borrar.
 * Cada cambio guarda en el outbox, en la misma transacción, el mensaje para el mcp-gateway.
 */
@Service
public class ProviderConfigLifecycle {

    private static final Logger log = LoggerFactory.getLogger(ProviderConfigLifecycle.class);

    private final OwnedConfigs owned;
    private final ProviderConfigRepository configs;
    private final CredentialVault vault;
    private final AiEventOutbox outbox;
    private final Clock clock;

    ProviderConfigLifecycle(OwnedConfigs owned, ProviderConfigRepository configs, CredentialVault vault,
                            AiEventOutbox outbox, Clock clock) {
        this.owned = owned;
        this.configs = configs;
        this.vault = vault;
        this.outbox = outbox;
        this.clock = clock;
    }

    /** La key anterior se borra en la misma transacción: una key revocada no queda guardada. */
    @Transactional
    public ProviderConfigView rotateCredential(UUID userId, UUID configId, String apiKey, Long expectedVersion) {
        ProviderConfig config = owned.lock(userId, configId, expectedVersion);
        UUID previous = config.credentialId();
        Instant now = clock.instant();
        StoredCredential credential = vault.store(userId, apiKey, now);
        config.replaceCredential(credential.id(), now);
        configs.save(config);
        vault.delete(previous);
        outbox.configChanged(ConfigChange.CREDENTIAL_ROTATED, config);
        return ProviderConfigView.of(config, credential.hint());
    }

    /**
     * Pide al mcp-gateway un health_check. El resultado llega con {@code ai.provider.test-completed} y el mismo
     * correlationId; mientras tanto {@code lastTest.success} es {@code null}.
     */
    @Transactional
    public ProviderConfigView requestTest(UUID userId, UUID configId) {
        ProviderConfig config = owned.lock(userId, configId, null);
        if (!config.enabled()) {
            throw AiConfigException.configDisabled();
        }
        UUID correlationId = UUID.randomUUID();
        config.startTest(correlationId, clock.instant());
        configs.save(config);
        outbox.testRequested(config, correlationId);
        return owned.view(config);
    }

    @Transactional
    public void delete(UUID userId, UUID configId) {
        ProviderConfig config = owned.lock(userId, configId, null);
        config.retire();
        outbox.configChanged(ConfigChange.DELETED, config);
        configs.delete(configId);
        vault.delete(config.credentialId());
        log.info("Configuración de IA {} eliminada", configId);
    }

    /** {@code user.deleted}: idempotente. */
    @Transactional
    public void deleteAll(UUID userId) {
        for (ProviderConfig config : configs.findByUser(userId)) {
            config.retire();
            outbox.configChanged(ConfigChange.DELETED, config);
        }
        configs.deleteByUser(userId);
        vault.deleteByUser(userId);
    }
}

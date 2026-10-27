package com.aplicafacil.aiconfig.application;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aplicafacil.aiconfig.application.port.out.AiEventOutbox;
import com.aplicafacil.aiconfig.application.port.out.AiEventOutbox.ConfigChange;
import com.aplicafacil.aiconfig.application.port.out.CredentialVault;
import com.aplicafacil.aiconfig.application.port.out.CredentialVault.StoredCredential;
import com.aplicafacil.aiconfig.application.port.out.ProviderConfigRepository;
import com.aplicafacil.aiconfig.domain.ModelParameters;
import com.aplicafacil.aiconfig.domain.ProviderConfig;
import com.aplicafacil.aiconfig.domain.ProviderType;

/**
 * Alta y edición de las configuraciones de una persona (las consultas están en {@link ProviderConfigQueries}). Qué datos se aceptan lo decide
 * {@link ProviderSettingsRules}; las reglas de versión y estado, {@link ProviderConfig}. Rotar la key, probar y borrar
 * están en {@link ProviderConfigLifecycle}.
 */
@Service
public class ProviderConfigService {

    private static final Logger log = LoggerFactory.getLogger(ProviderConfigService.class);

    private final OwnedConfigs owned;
    private final ProviderConfigRepository configs;
    private final ProviderSettingsRules rules;
    private final CredentialVault vault;
    private final AiEventOutbox outbox;
    private final Clock clock;

    ProviderConfigService(OwnedConfigs owned, ProviderConfigRepository configs, ProviderSettingsRules rules,
                          CredentialVault vault, AiEventOutbox outbox, Clock clock) {
        this.owned = owned;
        this.configs = configs;
        this.rules = rules;
        this.vault = vault;
        this.outbox = outbox;
        this.clock = clock;
    }

    @Transactional
    public ProviderConfigView create(UUID userId, NewConfig request) {
        ProviderType type = rules.providerType(request.providerType());
        String name = request.displayName().strip();
        ProviderConfig.Settings settings = rules.settings(type, name, request.model(), request.baseUrl(),
                request.parameters());
        rules.checkNewConfig(userId, name);
        Instant now = clock.instant();
        StoredCredential credential = vault.store(userId, request.apiKey(), now);
        ProviderConfig config = ProviderConfig.create(userId, type.type(), settings, request.enabled(),
                credential.id(), now);
        configs.save(config);
        outbox.configChanged(ConfigChange.CREATED, config);
        log.info("Configuración de IA {} creada ({}, v{})", config.id(), type.type(), config.version());
        return ProviderConfigView.of(config, credential.hint());
    }

    @Transactional
    public ProviderConfigView update(UUID userId, UUID configId, ConfigChanges changes) {
        ProviderConfig config = owned.lock(userId, configId, changes.expectedVersion());
        ProviderConfig.Snapshot current = config.snapshot();
        ProviderType type = rules.providerType(current.providerType());
        String name = changes.displayName().strip();
        ProviderConfig.Settings settings = rules.settings(type, name, changes.model(), changes.baseUrl(),
                changes.parameters());
        rules.checkRename(userId, current.settings().displayName(), name);
        config.edit(settings, clock.instant());
        return saveAndPublish(config, ConfigChange.UPDATED);
    }

    @Transactional
    public ProviderConfigView setEnabled(UUID userId, UUID configId, boolean enabled) {
        ProviderConfig config = owned.lock(userId, configId, null);
        if (!config.changeEnabled(enabled, clock.instant())) {
            return owned.view(config);
        }
        return saveAndPublish(config, enabled ? ConfigChange.UPDATED : ConfigChange.DISABLED);
    }

    private ProviderConfigView saveAndPublish(ProviderConfig config, ConfigChange change) {
        configs.save(config);
        outbox.configChanged(change, config);
        return owned.view(config);
    }

    /** Alta. {@code apiKey} solo vive en memoria hasta cifrarla. */
    public record NewConfig(String providerType, String displayName, String apiKey, String model, String baseUrl,
                            ModelParameters parameters, boolean enabled) {

        @Override
        public String toString() {
            return "NewConfig[providerType=" + providerType + ", displayName=" + displayName + "]";
        }
    }

    public record ConfigChanges(String displayName, String model, String baseUrl, ModelParameters parameters,
                                Long expectedVersion) {
    }
}

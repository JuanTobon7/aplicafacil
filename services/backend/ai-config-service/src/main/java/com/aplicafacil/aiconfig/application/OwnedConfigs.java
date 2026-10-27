package com.aplicafacil.aiconfig.application;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.aplicafacil.aiconfig.application.exception.AiConfigException;
import com.aplicafacil.aiconfig.application.port.out.CredentialVault;
import com.aplicafacil.aiconfig.application.port.out.ProviderConfigRepository;
import com.aplicafacil.aiconfig.domain.ProviderConfig;

/**
 * Acceso a las configuraciones de una persona que comparten los casos de uso: una de otra persona responde igual que
 * una inexistente, y la vista nunca lleva la key (solo su pista).
 */
@Component
class OwnedConfigs {

    private final ProviderConfigRepository configs;
    private final CredentialVault vault;

    OwnedConfigs(ProviderConfigRepository configs, CredentialVault vault) {
        this.configs = configs;
        this.vault = vault;
    }

    ProviderConfig get(UUID userId, UUID configId) {
        return configs.findOwned(configId, userId).orElseThrow(AiConfigException::configNotFound);
    }

    /** Bloquea la fila y comprueba la versión que espera el cliente ({@code null} = no comprobar). */
    ProviderConfig lock(UUID userId, UUID configId, Long expectedVersion) {
        ProviderConfig config = configs.findForUpdate(configId)
                .filter(c -> c.userId().equals(userId))
                .orElseThrow(AiConfigException::configNotFound);
        if (!config.isAtVersion(expectedVersion)) {
            throw AiConfigException.versionConflict();
        }
        return config;
    }

    ProviderConfigView view(ProviderConfig config) {
        return ProviderConfigView.of(config, vault.hint(config.credentialId()).orElse(null));
    }
}

package com.aplicafacil.aiconfig.application;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aplicafacil.aiconfig.application.port.out.CredentialVault;
import com.aplicafacil.aiconfig.application.port.out.ProviderConfigRepository;
import com.aplicafacil.aiconfig.domain.ProviderConfig;
import com.aplicafacil.aiconfig.domain.ProviderType;

/** Lo que el front consulta: tipos de proveedor y las configuraciones de la persona (sin la key, solo su pista). */
@Service
public class ProviderConfigQueries {

    private final OwnedConfigs owned;
    private final ProviderConfigRepository configs;
    private final CredentialVault vault;
    private final ProviderSettingsRules rules;

    ProviderConfigQueries(OwnedConfigs owned, ProviderConfigRepository configs, CredentialVault vault,
                          ProviderSettingsRules rules) {
        this.owned = owned;
        this.configs = configs;
        this.vault = vault;
        this.rules = rules;
    }

    public List<ProviderType> providerTypes() {
        return rules.providerTypes();
    }

    @Transactional(readOnly = true)
    public List<ProviderConfigView> list(UUID userId) {
        List<ProviderConfig> mine = configs.findByUser(userId);
        Map<UUID, String> hints = vault.hints(mine.stream().map(ProviderConfig::credentialId).toList());
        return mine.stream().map(c -> ProviderConfigView.of(c, hints.get(c.credentialId()))).toList();
    }

    @Transactional(readOnly = true)
    public ProviderConfigView get(UUID userId, UUID configId) {
        return owned.view(owned.get(userId, configId));
    }
}

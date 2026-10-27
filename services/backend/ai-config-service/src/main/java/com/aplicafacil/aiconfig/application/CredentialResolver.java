package com.aplicafacil.aiconfig.application;

import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aplicafacil.aiconfig.application.exception.AiConfigException;
import com.aplicafacil.aiconfig.application.port.out.CredentialVault;
import com.aplicafacil.aiconfig.application.port.out.ProviderConfigRepository;
import com.aplicafacil.aiconfig.domain.ModelParameters;
import com.aplicafacil.aiconfig.domain.ProviderConfig;
import com.aplicafacil.aiconfig.domain.ProviderConfigStatus;

/**
 * Lo que el mcp-gateway puede leer ({@code /internal/provider-configs}). Es el único caso de uso que entrega una
 * API key, y solo si la configuración pertenece al {@code userId} indicado y quien lee está autorizado.
 */
@Service
public class CredentialResolver {

    private static final Logger log = LoggerFactory.getLogger(CredentialResolver.class);

    private final ProviderConfigRepository configs;
    private final CredentialVault vault;
    private final AiPolicy policy;

    public CredentialResolver(ProviderConfigRepository configs, CredentialVault vault, AiPolicy policy) {
        this.configs = configs;
        this.vault = vault;
        this.policy = policy;
    }

    /** Solo los servicios de {@code aplicafacil.ai.credential-readers}. */
    public void authorize(String serviceSubject) {
        if (!policy.canReadCredentials(serviceSubject)) {
            throw AiConfigException.forbiddenReader();
        }
    }

    /** Sin credenciales: para reconciliar (todas) o para las tools MCP (las de un usuario). */
    @Transactional(readOnly = true)
    public List<ConfigSummary> summaries(UUID userId) {
        List<ProviderConfig> list = userId == null ? configs.findAll() : configs.findByUser(userId);
        return list.stream().map(ConfigSummary::of).toList();
    }

    @Transactional(readOnly = true)
    public ResolvedConfig resolve(UUID configId, UUID userId, String reader) {
        ProviderConfig config = configs.findOwned(configId, userId).orElseThrow(AiConfigException::configNotFound);
        String apiKey = vault.reveal(config.credentialId());
        log.info("Credencial de la configuración {} (v{}) entregada a {}", configId, config.version(), reader);
        ProviderConfig.Snapshot c = config.snapshot();
        ProviderConfig.Settings s = c.settings();
        return new ResolvedConfig(c.id(), c.userId(), c.providerType(), s.displayName(), c.enabled(), s.model(),
                s.baseUrl(), s.parameters(), c.version(), apiKey);
    }

    public record ConfigSummary(UUID id, UUID userId, String providerType, String displayName, boolean enabled,
                                String model, long version, ProviderConfigStatus status,
                                List<String> availableModels) {

        static ConfigSummary of(ProviderConfig config) {
            ProviderConfig.Snapshot c = config.snapshot();
            return new ConfigSummary(c.id(), c.userId(), c.providerType(), c.settings().displayName(), c.enabled(),
                    c.settings().model(), c.version(), c.status().value(), c.availableModels());
        }
    }

    /** Contiene el secreto: su {@code toString} no lo imprime. */
    public record ResolvedConfig(UUID id, UUID userId, String providerType, String displayName, boolean enabled,
                                 String model, String baseUrl, ModelParameters parameters, long version,
                                 String apiKey) {

        @Override
        public String toString() {
            return "ResolvedConfig[id=" + id + ", providerType=" + providerType + ", version=" + version + "]";
        }
    }
}

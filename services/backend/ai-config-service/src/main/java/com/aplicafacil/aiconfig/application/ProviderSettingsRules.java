package com.aplicafacil.aiconfig.application;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.aplicafacil.aiconfig.application.exception.AiConfigException;
import com.aplicafacil.aiconfig.application.port.out.ConfigQuota;
import com.aplicafacil.aiconfig.domain.ModelParameters;
import com.aplicafacil.aiconfig.domain.ProviderConfig;
import com.aplicafacil.aiconfig.domain.ProviderType;

/**
 * Qué datos acepta una configuración antes de guardarla: tipo de proveedor conocido, URL base permitida, límite por
 * persona y nombre sin repetir. {@link ProviderConfigService} la usa en el alta y en la edición.
 */
@Component
public class ProviderSettingsRules {

    private final AiPolicy policy;
    private final ConfigQuota quota;

    public ProviderSettingsRules(AiPolicy policy, ConfigQuota quota) {
        this.policy = policy;
        this.quota = quota;
    }

    public List<ProviderType> providerTypes() {
        return policy.providerTypes();
    }

    public ProviderType providerType(String type) {
        return policy.providerType(type).orElseThrow(() -> AiConfigException.unknownProviderType(type));
    }

    /** Alta: no superar el máximo por persona ni repetir un nombre. */
    public void checkNewConfig(UUID userId, String name) {
        if (quota.countByUser(userId) >= policy.maxConfigsPerUser()) {
            throw AiConfigException.tooManyConfigs(policy.maxConfigsPerUser());
        }
        if (quota.nameTaken(userId, name)) {
            throw AiConfigException.duplicateName();
        }
    }

    /** Edición: el nombre nuevo no puede ser el de otra configuración (cambiar mayúsculas del propio sí). */
    public void checkRename(UUID userId, String currentName, String newName) {
        if (!newName.equalsIgnoreCase(currentName) && quota.nameTaken(userId, newName)) {
            throw AiConfigException.duplicateName();
        }
    }

    /** Ajustes ya validados, con el modelo por defecto del tipo si no se indicó uno. */
    public ProviderConfig.Settings settings(ProviderType type, String name, String model, String baseUrl,
                                            ModelParameters parameters) {
        return new ProviderConfig.Settings(name, type.modelOrDefault(model), BaseUrls.validate(type, baseUrl),
                parameters);
    }
}

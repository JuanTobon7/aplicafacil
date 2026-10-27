package com.aplicafacil.aiconfig.domain;

import java.util.List;

/**
 * Un tipo de proveedor válido (catálogo configurable: agregar uno no exige tocar código).
 *
 * @param allowsBaseUrl si la persona puede indicar otra URL (APIs compatibles con OpenAI)
 */
public record ProviderType(
        String type,
        String displayName,
        boolean allowsBaseUrl,
        String defaultModel,
        List<String> suggestedModels) {

    public ProviderType {
        suggestedModels = suggestedModels == null ? List.of() : List.copyOf(suggestedModels);
    }

    /** El modelo indicado, o el de este tipo si viene vacío. */
    public String modelOrDefault(String model) {
        return model == null || model.isBlank() ? defaultModel : model.strip();
    }
}

package com.aplicafacil.aiconfig.domain;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Parámetros por defecto del modelo. Todos opcionales: {@code null} = el valor por defecto del proveedor.
 * Se guardan como JSONB en {@code provider_config.parameters}.
 */
public record ModelParameters(
        @DecimalMin("0.0") @DecimalMax("2.0") Double temperature,
        @Min(1) @Max(200_000) Integer maxTokens,
        @DecimalMin("0.0") @DecimalMax("1.0") Double topP) {

    public static ModelParameters empty() {
        return new ModelParameters(null, null, null);
    }
}

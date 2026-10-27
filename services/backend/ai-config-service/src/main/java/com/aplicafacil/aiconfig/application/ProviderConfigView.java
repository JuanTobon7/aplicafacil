package com.aplicafacil.aiconfig.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.aplicafacil.aiconfig.domain.ModelParameters;
import com.aplicafacil.aiconfig.domain.ProviderConfig;
import com.aplicafacil.aiconfig.domain.ProviderConfigStatus;

/**
 * Lo que ve el front de una configuración. No tiene campo para la API key: solo {@code credentialHint}.
 *
 * @param lastTest {@code null} si nunca se probó; {@code success = null} mientras la prueba está en curso
 */
public record ProviderConfigView(
        UUID id,
        String providerType,
        String displayName,
        boolean enabled,
        String model,
        String baseUrl,
        ModelParameters parameters,
        String credentialHint,
        long version,
        ProviderConfigStatus status,
        String statusCode,
        String statusMessage,
        Instant statusUpdatedAt,
        List<String> availableModels,
        TestResult lastTest,
        Instant createdAt,
        Instant updatedAt) {

    public record TestResult(Instant at, Boolean success, Integer latencyMs, String code, String message) {
    }

    static ProviderConfigView of(ProviderConfig config, String hint) {
        ProviderConfig.Snapshot c = config.snapshot();
        ProviderConfig.TestRun t = c.lastTest();
        TestResult test = t == null || t.at() == null ? null
                : new TestResult(t.at(), t.success(), t.latencyMs(), t.code(), t.message());
        ProviderConfig.Settings s = c.settings();
        ProviderConfig.Status st = c.status();
        return new ProviderConfigView(c.id(), c.providerType(), s.displayName(), c.enabled(), s.model(), s.baseUrl(),
                s.parameters(), hint == null ? null : "••••" + hint, c.version(), st.value(), st.code(), st.message(),
                st.updatedAt(), c.availableModels(), test, c.createdAt(), c.updatedAt());
    }
}

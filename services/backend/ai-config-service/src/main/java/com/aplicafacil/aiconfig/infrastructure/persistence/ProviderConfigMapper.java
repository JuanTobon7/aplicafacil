package com.aplicafacil.aiconfig.infrastructure.persistence;

import com.aplicafacil.aiconfig.domain.ProviderConfig;
import com.aplicafacil.aiconfig.domain.ProviderConfig.Settings;
import com.aplicafacil.aiconfig.domain.ProviderConfig.Snapshot;
import com.aplicafacil.aiconfig.domain.ProviderConfig.Status;
import com.aplicafacil.aiconfig.domain.ProviderConfig.TestRun;
import com.aplicafacil.aiconfig.infrastructure.persistence.entity.ProviderConfigEntity;

/** Traduce entre la fila {@code provider_config} y el dominio. */
final class ProviderConfigMapper {

    private ProviderConfigMapper() {
    }

    static ProviderConfig toDomain(ProviderConfigEntity e) {
        Settings settings = new Settings(e.getDisplayName(), e.getModel(), e.getBaseUrl(), e.getParameters());
        Status status = new Status(e.getStatus(), e.getStatusCode(), e.getStatusMessage(), e.getStatusUpdatedAt());
        TestRun test = new TestRun(e.getLastTestCorrelationId(), e.getLastTestAt(), e.getLastTestSuccess(),
                e.getLastTestLatencyMs(), e.getLastTestCode(), e.getLastTestMessage());
        return ProviderConfig.restore(new Snapshot(e.getId(), e.getUserId(), e.getProviderType(), settings,
                e.isEnabled(), e.getCredentialId(), e.getVersion(), status,
                e.getMetadata() == null ? null : e.getMetadata().availableModels(), test, e.getCreatedAt(),
                e.getUpdatedAt()));
    }

    static void copy(Snapshot s, ProviderConfigEntity e) {
        e.setId(s.id());
        e.setUserId(s.userId());
        e.setProviderType(s.providerType());
        e.setEnabled(s.enabled());
        e.setCredentialId(s.credentialId());
        e.setVersion(s.version());
        e.setMetadata(new ProviderConfigEntity.Metadata(s.availableModels()));
        e.setCreatedAt(s.createdAt());
        e.setUpdatedAt(s.updatedAt());
        copySettings(s.settings(), e);
        copyStatus(s.status(), e);
        copyTest(s.lastTest(), e);
    }

    private static void copySettings(Settings settings, ProviderConfigEntity e) {
        e.setDisplayName(settings.displayName());
        e.setModel(settings.model());
        e.setBaseUrl(settings.baseUrl());
        e.setParameters(settings.parameters());
    }

    private static void copyStatus(Status status, ProviderConfigEntity e) {
        e.setStatus(status.value());
        e.setStatusCode(status.code());
        e.setStatusMessage(status.message());
        e.setStatusUpdatedAt(status.updatedAt());
    }

    private static void copyTest(TestRun test, ProviderConfigEntity e) {
        TestRun t = test == null ? new TestRun(null, null, null, null, null, null) : test;
        e.setLastTestCorrelationId(t.correlationId());
        e.setLastTestAt(t.at());
        e.setLastTestSuccess(t.success());
        e.setLastTestLatencyMs(t.latencyMs());
        e.setLastTestCode(t.code());
        e.setLastTestMessage(t.message());
    }
}

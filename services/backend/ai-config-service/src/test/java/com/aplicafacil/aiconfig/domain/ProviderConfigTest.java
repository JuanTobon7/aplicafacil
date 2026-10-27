package com.aplicafacil.aiconfig.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/** Reglas de versión y estado de una configuración, sin Spring ni base de datos. */
class ProviderConfigTest {

    private final Instant now = Instant.parse("2026-10-19T12:00:00Z");

    @Test
    void everyChangeBumpsTheVersionAndGoesBackToPending() {
        ProviderConfig config = enabledConfig();
        config.applyStatus(report(1, ProviderConfigStatus.ACTIVE), now);

        config.edit(new ProviderConfig.Settings("Otra", "gpt-4o", null, null), now);

        assertThat(config.version()).isEqualTo(2);
        assertThat(config.snapshot().status().value()).isEqualTo(ProviderConfigStatus.PENDING);
    }

    @Test
    void aStatusForAnOldVersionIsIgnored() {
        ProviderConfig config = enabledConfig();
        config.edit(new ProviderConfig.Settings("Otra", "gpt-4o", null, null), now);

        assertThat(config.applyStatus(report(1, ProviderConfigStatus.ACTIVE), now)).isFalse();
        assertThat(config.snapshot().status().value()).isEqualTo(ProviderConfigStatus.PENDING);
    }

    @Test
    void initializingNeverMovesAnActiveConfigBack() {
        ProviderConfig config = enabledConfig();
        config.applyStatus(report(1, ProviderConfigStatus.ACTIVE), now);

        assertThat(config.applyStatus(report(1, ProviderConfigStatus.INITIALIZING), now)).isFalse();
        assertThat(config.snapshot().status().value()).isEqualTo(ProviderConfigStatus.ACTIVE);
    }

    @Test
    void aDisabledConfigStaysDisabled() {
        ProviderConfig config = enabledConfig();
        config.changeEnabled(false, now);

        assertThat(config.applyStatus(report(2, ProviderConfigStatus.ACTIVE), now)).isFalse();
        assertThat(config.snapshot().status().value()).isEqualTo(ProviderConfigStatus.DISABLED);
    }

    @Test
    void onlyTheMatchingCorrelationIdClosesATest() {
        ProviderConfig config = enabledConfig();
        UUID correlation = UUID.randomUUID();
        config.startTest(correlation, now);
        ProviderConfig.TestOutcome ok = new ProviderConfig.TestOutcome(true, 120, null, null);

        assertThat(config.completeTest(UUID.randomUUID(), ok, now)).isFalse();
        assertThat(config.completeTest(correlation, ok, now)).isTrue();
        assertThat(config.completeTest(correlation, ok, now)).isFalse();
        assertThat(config.snapshot().lastTest().success()).isTrue();
    }

    @Test
    void rotatingTheKeyForgetsTheModelsItCouldSee() {
        ProviderConfig config = enabledConfig();
        config.applyStatus(new ProviderConfig.StatusReport(1, ProviderConfigStatus.ACTIVE, null, null,
                List.of("gpt-4o")), now);

        config.replaceCredential(UUID.randomUUID(), now);

        assertThat(config.snapshot().availableModels()).isEmpty();
    }

    private ProviderConfig enabledConfig() {
        return ProviderConfig.create(UUID.randomUUID(), "openai",
                new ProviderConfig.Settings("Mi OpenAI", "gpt-4o-mini", null, null), true, UUID.randomUUID(), now);
    }

    private static ProviderConfig.StatusReport report(long version, ProviderConfigStatus status) {
        return new ProviderConfig.StatusReport(version, status, null, null, null);
    }
}

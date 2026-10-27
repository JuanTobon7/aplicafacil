package com.aplicafacil.aiconfig.domain;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Configuración de un proveedor de IA de una persona. Es la experta en su ciclo de vida (GRASP): cada cambio
 * incrementa {@code version} y la deja PENDING (o DISABLED), y decide qué estados del mcp-gateway acepta:
 *
 * <ul>
 *   <li>Un estado de una versión distinta de la vigente se ignora (llegó tarde).</li>
 *   <li>{@code INITIALIZING} solo reemplaza a {@code PENDING}: no hace retroceder un {@code ACTIVE}.</li>
 *   <li>Una configuración desactivada sigue {@code DISABLED} aunque llegue un estado.</li>
 *   <li>Un resultado de prueba solo cierra la prueba con su mismo correlationId.</li>
 * </ul>
 */
public final class ProviderConfig {

    private static final int MAX_MODELS = 500;
    private static final int MAX_CODE = 64;
    private static final int MAX_MESSAGE = 500;

    private final UUID id;
    private final UUID userId;
    private final String providerType;
    private final Instant createdAt;
    private Settings settings;
    private boolean enabled;
    private UUID credentialId;
    private long version;
    private Status status;
    private List<String> availableModels;
    private TestRun lastTest;
    private Instant updatedAt;

    private ProviderConfig(Snapshot s) {
        this.id = Objects.requireNonNull(s.id());
        this.userId = Objects.requireNonNull(s.userId());
        this.providerType = Objects.requireNonNull(s.providerType());
        this.createdAt = s.createdAt();
        this.settings = s.settings();
        this.enabled = s.enabled();
        this.credentialId = s.credentialId();
        this.version = s.version();
        this.status = s.status();
        this.availableModels = s.availableModels() == null ? List.of() : List.copyOf(s.availableModels());
        this.lastTest = s.lastTest();
        this.updatedAt = s.updatedAt();
    }

    public static ProviderConfig create(UUID userId, String providerType, Settings settings, boolean enabled,
                                        UUID credentialId, Instant now) {
        return new ProviderConfig(new Snapshot(UUID.randomUUID(), userId, providerType, settings, enabled,
                credentialId, 1, Status.initial(enabled, now), List.of(), null, now, now));
    }

    /** Reconstruye una configuración guardada (lo usa el adaptador de persistencia). */
    public static ProviderConfig restore(Snapshot snapshot) {
        return new ProviderConfig(snapshot);
    }

    /** Concurrencia optimista pedida por el cliente: {@code null} = no comprobar. */
    public boolean isAtVersion(Long expected) {
        return expected == null || expected == version;
    }

    public void edit(Settings newSettings, Instant now) {
        settings = Objects.requireNonNull(newSettings);
        bump(now);
    }

    /** Los modelos disponibles dependen de la key: se vuelven a informar al inicializar. */
    public void replaceCredential(UUID newCredential, Instant now) {
        credentialId = Objects.requireNonNull(newCredential);
        availableModels = List.of();
        bump(now);
    }

    /** @return {@code false} si ya estaba así (no hay cambio que publicar) */
    public boolean changeEnabled(boolean value, Instant now) {
        if (enabled == value) {
            return false;
        }
        enabled = value;
        bump(now);
        return true;
    }

    /** Antes de borrarla: la versión del evento de borrado supera a cualquier estado anterior. */
    public void retire() {
        version++;
        enabled = false;
    }

    public void startTest(UUID correlationId, Instant now) {
        lastTest = TestRun.started(correlationId, now);
    }

    /** @return {@code true} si el estado informado cambió algo */
    public boolean applyStatus(StatusReport report, Instant now) {
        if (report.configVersion() != version || !enabled) {
            return false;
        }
        Status next = switch (report.status()) {
            case INITIALIZING -> status.value() == ProviderConfigStatus.PENDING
                    ? new Status(ProviderConfigStatus.INITIALIZING, status.code(), status.message(), now) : null;
            case ACTIVE -> activate(report.models(), now);
            case FAILED -> new Status(ProviderConfigStatus.FAILED, truncate(report.errorCode(), MAX_CODE),
                    truncate(report.errorMessage(), MAX_MESSAGE), now);
            default -> null;
        };
        if (next == null) {
            return false;
        }
        status = next;
        return true;
    }

    private Status activate(List<String> models, Instant now) {
        if (models != null) {
            availableModels = models.stream().limit(MAX_MODELS).toList();
        }
        return new Status(ProviderConfigStatus.ACTIVE, null, null, now);
    }

    /** @return {@code true} si cerró la prueba en curso con ese correlationId */
    public boolean completeTest(UUID correlationId, TestOutcome outcome, Instant now) {
        if (lastTest == null || !lastTest.inProgressWith(correlationId)) {
            return false;
        }
        lastTest = new TestRun(correlationId, now, outcome.success(), outcome.latencyMs(),
                truncate(outcome.code(), MAX_CODE), truncate(outcome.message(), MAX_MESSAGE));
        return true;
    }

    /** Prueba sin respuesta (mcp-gateway apagado): se cierra como fallida para no quedar "en curso" siempre. */
    public void expireTest() {
        if (lastTest != null && lastTest.success() == null) {
            lastTest = new TestRun(lastTest.correlationId(), lastTest.at(), false, null, "TIMEOUT",
                    "El mcp-gateway no respondió a tiempo. ¿Está corriendo?");
        }
    }

    private void bump(Instant now) {
        version++;
        status = Status.initial(enabled, now);
        updatedAt = now;
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }

    public UUID id() {
        return id;
    }

    public UUID userId() {
        return userId;
    }

    public boolean enabled() {
        return enabled;
    }

    public UUID credentialId() {
        return credentialId;
    }

    public long version() {
        return version;
    }

    public Snapshot snapshot() {
        return new Snapshot(id, userId, providerType, settings, enabled, credentialId, version, status,
                availableModels, lastTest, createdAt, updatedAt);
    }

    /** Lo que la persona elige. */
    public record Settings(String displayName, String model, String baseUrl, ModelParameters parameters) {

        public Settings {
            parameters = parameters == null ? ModelParameters.empty() : parameters;
        }
    }

    /** Estado informado por el mcp-gateway; {@code code} es estable (AUTHENTICATION_FAILED…). */
    public record Status(ProviderConfigStatus value, String code, String message, Instant updatedAt) {

        static Status initial(boolean enabled, Instant now) {
            return new Status(enabled ? ProviderConfigStatus.PENDING : ProviderConfigStatus.DISABLED, null, null, now);
        }
    }

    /** Última prueba de conexión; {@code success = null} mientras está en curso. */
    public record TestRun(UUID correlationId, Instant at, Boolean success, Integer latencyMs, String code,
                          String message) {

        static TestRun started(UUID correlationId, Instant now) {
            return new TestRun(correlationId, now, null, null, null, null);
        }

        boolean inProgressWith(UUID correlation) {
            return success == null && correlationId != null && correlationId.equals(correlation);
        }
    }

    /** {@code ai.provider.initialization-*} ya leído. */
    public record StatusReport(long configVersion, ProviderConfigStatus status, String errorCode,
                               String errorMessage, List<String> models) {
    }

    /** {@code ai.provider.test-completed} ya leído. */
    public record TestOutcome(boolean success, Integer latencyMs, String code, String message) {
    }

    /** Estado completo, para guardar y reconstruir. */
    public record Snapshot(UUID id, UUID userId, String providerType, Settings settings, boolean enabled,
                           UUID credentialId, long version, Status status, List<String> availableModels,
                           TestRun lastTest, Instant createdAt, Instant updatedAt) {
    }
}

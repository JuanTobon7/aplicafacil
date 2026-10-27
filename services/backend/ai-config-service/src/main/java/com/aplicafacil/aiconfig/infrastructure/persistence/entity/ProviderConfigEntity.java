package com.aplicafacil.aiconfig.infrastructure.persistence.entity;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.aplicafacil.aiconfig.domain.ModelParameters;
import com.aplicafacil.aiconfig.domain.ProviderConfigStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Configuración de un proveedor de IA de un usuario. {@code version} es la versión de negocio que viaja en los
 * eventos: el mcp-gateway la usa para descartar cambios repetidos o atrasados.
 */
@Entity
@Table(name = "provider_config")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProviderConfigEntity {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "provider_type", nullable = false, length = 32)
    private String providerType;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @Column(nullable = false)
    private boolean enabled;

    @Column(nullable = false, length = 200)
    private String model;

    @Column(name = "base_url", length = 2048)
    private String baseUrl;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private ModelParameters parameters;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private Metadata metadata;

    @Column(name = "credential_id", nullable = false)
    private UUID credentialId;

    @Column(nullable = false)
    private long version;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ProviderConfigStatus status;

    /** Código estable del último error (AUTHENTICATION_FAILED, PROVIDER_UNAVAILABLE…). */
    @Column(name = "status_code", length = 64)
    private String statusCode;

    /** Mensaje ya saneado por el mcp-gateway. */
    @Column(name = "status_message", length = 500)
    private String statusMessage;

    @Column(name = "status_updated_at", nullable = false)
    private Instant statusUpdatedAt;

    @Column(name = "last_test_at")
    private Instant lastTestAt;

    @Column(name = "last_test_success")
    private Boolean lastTestSuccess;

    @Column(name = "last_test_latency_ms")
    private Integer lastTestLatencyMs;

    @Column(name = "last_test_code", length = 64)
    private String lastTestCode;

    @Column(name = "last_test_message", length = 500)
    private String lastTestMessage;

    /** Prueba en curso: el resultado con este correlationId la cierra. */
    @Column(name = "last_test_correlation_id")
    private UUID lastTestCorrelationId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Lo que informa el adaptador al inicializar: hoy, los modelos disponibles con esa key. */
    public record Metadata(List<String> availableModels) {

        public Metadata {
            availableModels = availableModels == null ? List.of() : List.copyOf(availableModels);
        }

        public static Metadata empty() {
            return new Metadata(new ArrayList<>());
        }
    }
}

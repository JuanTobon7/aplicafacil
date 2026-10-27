package com.aplicafacil.aiconfig.infrastructure.persistence.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * API key cifrada con {@code SessionCryptoService}. Rotar crea una fila nueva y borra la anterior: una key
 * revocada no queda guardada.
 */
@Entity
@Table(name = "provider_credential")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProviderCredentialEntity {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "secret_encrypted", nullable = false)
    private byte[] secretEncrypted;

    @Column(nullable = false)
    private byte[] iv;

    @Column(name = "key_version", nullable = false)
    private int keyVersion;

    /** Últimos 4 caracteres de la key; lo único que el front vuelve a ver. */
    @Column(nullable = false, length = 8)
    private String hint;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** Nunca imprime el secreto cifrado ni el IV. */
    @Override
    public String toString() {
        return "ProviderCredentialEntity[id=" + id + ", keyVersion=" + keyVersion + "]";
    }
}

package com.aplicafacil.identity.infrastructure.persistence.entity;

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
 * Código de un solo uso que el front canjea por tokens al terminar la conexión con LinkedIn.
 * Está ligado al intento de conexión ({@code attempt_id}) y vence en segundos.
 */
@Entity
@Table(name = "login_code")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginCodeEntity {

    @Id
    /** SHA-256 en hexadecimal. */
    @Column(name = "code_hash", columnDefinition = "bpchar(64)")
    private String codeHash;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "attempt_id", nullable = false)
    private UUID attemptId;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;
}

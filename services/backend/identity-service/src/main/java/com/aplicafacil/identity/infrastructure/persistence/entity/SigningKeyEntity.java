package com.aplicafacil.identity.infrastructure.persistence.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Par RS256 con el que se firman los JWT. La privada va cifrada y la pública se publica en el JWKS. */
@Entity
@Table(name = "signing_key")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SigningKeyEntity {

    @Id
    private String kid;

    @Column(nullable = false, length = 16)
    private String algorithm;

    /** X.509 SubjectPublicKeyInfo (DER). */
    @Column(name = "public_key", nullable = false)
    private byte[] publicKey;

    /** PKCS#8 (DER) cifrado con AES-256-GCM. */
    @Column(name = "private_key_encrypted", nullable = false)
    private byte[] privateKeyEncrypted;

    @Column(name = "private_key_iv", nullable = false)
    private byte[] privateKeyIv;

    @Column(name = "key_version", nullable = false)
    private int keyVersion;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "retired_at")
    private Instant retiredAt;
}

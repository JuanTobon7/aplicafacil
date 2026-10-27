package com.aplicafacil.identity.infrastructure.persistence.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.Setter;

/** Tokens OAuth de LinkedIn (fase desplegada), cifrados con {@code SessionCryptoService}. */
@Entity
@Table(name = "oauth_token")
@Getter
@Setter
public class OAuthTokenEntity {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 32)
    private String provider;

    @Column(name = "access_token_encrypted", nullable = false)
    private byte[] accessTokenEncrypted;

    @Column(name = "access_token_iv", nullable = false)
    private byte[] accessTokenIv;

    @Column(name = "refresh_token_encrypted")
    private byte[] refreshTokenEncrypted;

    @Column(name = "refresh_token_iv")
    private byte[] refreshTokenIv;

    @Column(name = "key_version", nullable = false)
    private int keyVersion;

    private String scope;

    @Column(name = "access_token_expires_at", nullable = false)
    private Instant accessTokenExpiresAt;

    @Column(name = "refresh_token_expires_at")
    private Instant refreshTokenExpiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}

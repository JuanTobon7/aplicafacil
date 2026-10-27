package com.aplicafacil.identity.infrastructure.persistence.entity;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Fila de {@code app_user}: un perfil de LinkedIn conectado = un usuario de la plataforma.
 * Al menos uno de los dos identificadores de LinkedIn tiene valor (CHECK en la BD).
 */
@Entity
@Table(name = "app_user")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppUserEntity {

    @Id
    private UUID id;

    @Column(name = "linkedin_member_urn", unique = true)
    private String linkedinMemberUrn;

    @Column(name = "linkedin_oidc_sub", unique = true)
    private String linkedinOidcSub;

    private String email;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified;

    @Column(name = "first_name")
    private String firstName;

    @Column(name = "last_name")
    private String lastName;

    private String headline;

    @Column(name = "photo_url")
    private String photoUrl;

    @Column(name = "public_profile_url")
    private String publicProfileUrl;

    private String location;

    /** Nombres de los campos básicos editados a mano; sincronizar no los sobrescribe. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "edited_fields", nullable = false)
    @Builder.Default
    private Set<String> editedFields = new LinkedHashSet<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Version
    private long version;
}

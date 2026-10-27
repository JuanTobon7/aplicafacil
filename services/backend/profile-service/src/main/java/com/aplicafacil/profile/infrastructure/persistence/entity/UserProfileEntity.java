package com.aplicafacil.profile.infrastructure.persistence.entity;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.aplicafacil.profile.domain.ProfileSection;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Perfil laboral: la raíz y sus entidades hijas (una tabla cada una). Las colecciones son LAZY y se cargan por lotes
 * ({@code default_batch_fetch_size}); el orden lo guarda {@code position} ({@link OrderColumn}).
 */
@Entity
@Table(name = "user_profile")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileEntity {

    private static final String OWNER = "user_id";
    private static final String POSITION = "position";

    @Id
    @Column(name = "user_id")
    private UUID userId;

    private String headline;

    private String summary;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = OWNER, nullable = false, updatable = false)
    @OrderColumn(name = POSITION, nullable = false)
    @Builder.Default
    private List<SkillEntity> skills = new ArrayList<>();

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = OWNER, nullable = false, updatable = false)
    @OrderColumn(name = POSITION, nullable = false)
    @Builder.Default
    private List<ExperienceEntity> experiences = new ArrayList<>();

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = OWNER, nullable = false, updatable = false)
    @OrderColumn(name = POSITION, nullable = false)
    @Builder.Default
    private List<EducationEntity> education = new ArrayList<>();

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = OWNER, nullable = false, updatable = false)
    @OrderColumn(name = POSITION, nullable = false)
    @Builder.Default
    private List<LanguageEntity> languages = new ArrayList<>();

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = OWNER, nullable = false, updatable = false)
    @OrderColumn(name = POSITION, nullable = false)
    @Builder.Default
    private List<CertificationEntity> certifications = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "manually_edited_fields", nullable = false)
    @Builder.Default
    private Set<ProfileSection> manuallyEditedFields = EnumSet.noneOf(ProfileSection.class);

    @Column(nullable = false)
    private boolean complete;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "missing_sections", nullable = false)
    @Builder.Default
    private List<String> missingSections = new ArrayList<>();

    @Column(name = "last_synced_at")
    private Instant lastSyncedAt;

    @Column(name = "cv_imported_at")
    private Instant cvImportedAt;

    @Embedded
    @Builder.Default
    private SearchPreferenceColumns search = new SearchPreferenceColumns();

    @Embedded
    @Builder.Default
    private WorkProfileColumns work = new WorkProfileColumns();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;
}

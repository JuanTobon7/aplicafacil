package com.aplicafacil.profile.infrastructure.persistence;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Function;

import org.springframework.stereotype.Repository;

import com.aplicafacil.profile.application.port.out.UserProfileRepository;
import com.aplicafacil.profile.domain.Certification;
import com.aplicafacil.profile.domain.Education;
import com.aplicafacil.profile.domain.Experience;
import com.aplicafacil.profile.domain.Language;
import com.aplicafacil.profile.domain.ProfileContent;
import com.aplicafacil.profile.domain.SearchPreference;
import com.aplicafacil.profile.domain.Skill;
import com.aplicafacil.profile.domain.UserProfile;
import com.aplicafacil.profile.domain.WorkProfile;
import com.aplicafacil.profile.infrastructure.persistence.entity.CertificationEntity;
import com.aplicafacil.profile.infrastructure.persistence.entity.EducationEntity;
import com.aplicafacil.profile.infrastructure.persistence.entity.ExperienceEntity;
import com.aplicafacil.profile.infrastructure.persistence.entity.LanguageEntity;
import com.aplicafacil.profile.infrastructure.persistence.entity.SearchPreferenceColumns;
import com.aplicafacil.profile.infrastructure.persistence.entity.SkillEntity;
import com.aplicafacil.profile.infrastructure.persistence.entity.UserProfileEntity;
import com.aplicafacil.profile.infrastructure.persistence.entity.WorkProfileColumns;

/**
 * Adaptador JPA del puerto {@link UserProfileRepository}: traduce entre las entidades y el dominio. Dentro de la
 * transacción del caso de uso, {@code findById} sale del contexto de persistencia (no vuelve a la BD).
 */
@Repository
public class JpaUserProfileRepository implements UserProfileRepository {

    private final SpringDataUserProfileRepository jpa;

    public JpaUserProfileRepository(SpringDataUserProfileRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Optional<UserProfile> findById(UUID userId) {
        return jpa.findById(userId).map(JpaUserProfileRepository::toDomain);
    }

    @Override
    public UserProfile save(UserProfile profile) {
        UserProfileEntity entity = jpa.findById(profile.userId()).orElseGet(() -> UserProfileEntity.builder()
                .userId(profile.userId())
                .createdAt(profile.createdAt())
                .build());
        copy(profile, entity);
        return toDomain(jpa.save(entity));
    }

    /** Un solo DELETE; las hijas se van por {@code ON DELETE CASCADE}. */
    @Override
    public void deleteById(UUID userId) {
        jpa.deleteByUserId(userId);
    }

    private static void copy(UserProfile from, UserProfileEntity to) {
        ProfileContent c = from.content();
        UserProfile.SyncState sync = from.syncState();
        to.setHeadline(c.headline());
        to.setSummary(c.summary());
        replace(to.getSkills(), c.skills(), new Sync<>(Skill::id, SkillEntity::getId,
                ProfileSectionsMapper::toEntity, ProfileSectionsMapper::update));
        replace(to.getExperiences(), c.experiences(), new Sync<>(Experience::id, ExperienceEntity::getId,
                ProfileSectionsMapper::toEntity, ProfileSectionsMapper::update));
        replace(to.getEducation(), c.education(), new Sync<>(Education::id, EducationEntity::getId,
                ProfileSectionsMapper::toEntity, ProfileSectionsMapper::update));
        replace(to.getLanguages(), c.languages(), new Sync<>(Language::id, LanguageEntity::getId,
                ProfileSectionsMapper::toEntity, ProfileSectionsMapper::update));
        replace(to.getCertifications(), c.certifications(), new Sync<>(Certification::id,
                CertificationEntity::getId, ProfileSectionsMapper::toEntity, ProfileSectionsMapper::update));
        to.setManuallyEditedFields(sync.manuallyEditedFields());
        to.setComplete(sync.complete());
        to.setMissingSections(new ArrayList<>(sync.missingSections()));
        to.setLastSyncedAt(sync.lastSyncedAt());
        to.setCvImportedAt(sync.cvImportedAt());
        to.setUpdatedAt(sync.updatedAt());
        copy(from.searchPreference(), to.getSearch());
        to.setWork(columns(from.workProfile()));
    }

    /**
     * Reemplaza la colección. Las filas cuyo id sigue en el dominio se actualizan en su lugar (mapa por id, en
     * memoria); las nuevas se insertan y las que ya no están se borran (orphanRemoval).
     */
    private static <D, E> void replace(List<E> current, List<D> wanted, Sync<D, E> sync) {
        Map<UUID, E> existing = new HashMap<>();
        current.forEach(e -> existing.put(sync.entityId().apply(e), e));
        List<E> next = new ArrayList<>(wanted.size());
        for (D item : wanted) {
            E kept = existing.get(sync.domainId().apply(item));
            if (kept == null) {
                next.add(sync.create().apply(item));
            } else {
                sync.update().accept(kept, item);
                next.add(kept);
            }
        }
        current.clear();
        current.addAll(next);
    }

    private static void copy(SearchPreference from, SearchPreferenceColumns to) {
        to.setKeywords(from.keywords());
        to.setAlternatives(new ArrayList<>(from.alternatives()));
        to.setRationale(from.rationale());
        to.setRemoteOnly(from.remoteOnly());
        to.setPostedWithinDays(from.postedWithinDays());
        to.setLocation(from.location());
        to.setSuggestedAt(from.suggestedAt());
    }

    private static UserProfile toDomain(UserProfileEntity e) {
        ProfileContent content = new ProfileContent(e.getHeadline(), e.getSummary(),
                e.getSkills().stream().map(ProfileSectionsMapper::toDomain).toList(),
                e.getExperiences().stream().map(ProfileSectionsMapper::toDomain).toList(),
                e.getEducation().stream().map(ProfileSectionsMapper::toDomain).toList(),
                e.getLanguages().stream().map(ProfileSectionsMapper::toDomain).toList(),
                e.getCertifications().stream().map(ProfileSectionsMapper::toDomain).toList());
        UserProfile.SyncState sync = new UserProfile.SyncState(e.getManuallyEditedFields(), e.isComplete(),
                e.getMissingSections(), e.getLastSyncedAt(), e.getCvImportedAt(), e.getUpdatedAt());
        return UserProfile.restore(e.getUserId(), e.getCreatedAt(), content, sync, preference(e.getSearch()),
                workProfile(e.getWork()));
    }

    private static SearchPreference preference(SearchPreferenceColumns s) {
        if (s == null) {
            return SearchPreference.NONE;
        }
        return new SearchPreference(s.getKeywords(), s.getAlternatives(), s.getRationale(), s.isRemoteOnly(),
                s.getPostedWithinDays(), s.getLocation(), s.getSuggestedAt());
    }

    private static WorkProfileColumns columns(WorkProfile from) {
        WorkProfileColumns to = new WorkProfileColumns();
        if (from != null) {
            to.setPrimaryRole(from.primaryRole());
            to.setSecondaryRoles(new ArrayList<>(from.secondaryRoles()));
            to.setSeniority(from.seniority());
            to.setTitle(from.title());
            to.setSource(from.source());
            to.setUpdatedAt(from.updatedAt());
        }
        return to;
    }

    /** Sin rol principal no hay perfil laboral (todavía no se sabe). */
    private static WorkProfile workProfile(WorkProfileColumns w) {
        if (w == null || w.getPrimaryRole() == null) {
            return null;
        }
        return new WorkProfile(w.getPrimaryRole(), w.getSecondaryRoles(), w.getSeniority(), w.getTitle(),
                w.getSource(), w.getUpdatedAt());
    }

    /** Cómo identificar, crear y actualizar una entidad hija a partir de su par de dominio. */
    private record Sync<D, E>(Function<D, UUID> domainId, Function<E, UUID> entityId, Function<D, E> create,
                              BiConsumer<E, D> update) {
    }
}

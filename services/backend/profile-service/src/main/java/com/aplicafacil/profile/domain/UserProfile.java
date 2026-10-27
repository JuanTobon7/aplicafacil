package com.aplicafacil.profile.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Perfil laboral. Es el experto en sus secciones (GRASP): decide qué copia de una lectura (LinkedIn o un CV) y qué
 * conserva, sin saber cómo se guarda ni de dónde vino la lectura.
 */
public final class UserProfile {

    private final UUID userId;
    private final Instant createdAt;
    private ProfileContent content = ProfileContent.EMPTY;
    private Set<ProfileSection> manuallyEditedFields = EnumSet.noneOf(ProfileSection.class);
    private boolean complete;
    private List<String> missingSections = new ArrayList<>();
    private Instant lastSyncedAt;
    private Instant cvImportedAt;
    private Instant updatedAt;
    private SearchPreference searchPreference = SearchPreference.NONE;
    private WorkProfile workProfile;

    private UserProfile(UUID userId, Instant createdAt) {
        this.userId = Objects.requireNonNull(userId);
        this.createdAt = Objects.requireNonNull(createdAt);
    }

    public static UserProfile create(UUID userId, Instant now) {
        UserProfile profile = new UserProfile(userId, now);
        profile.updatedAt = now;
        return profile;
    }

    /** Reconstruye un perfil guardado (lo usa el adaptador de persistencia). */
    public static UserProfile restore(UUID userId, Instant createdAt, ProfileContent content, SyncState sync,
                                      SearchPreference preference, WorkProfile workProfile) {
        UserProfile profile = new UserProfile(userId, createdAt);
        profile.content = content;
        profile.manuallyEditedFields = enumSet(sync.manuallyEditedFields());
        profile.complete = sync.complete();
        profile.missingSections = new ArrayList<>(sync.missingSections());
        profile.lastSyncedAt = sync.lastSyncedAt();
        profile.cvImportedAt = sync.cvImportedAt();
        profile.updatedAt = sync.updatedAt();
        profile.searchPreference = preference == null ? SearchPreference.NONE : preference;
        profile.workProfile = workProfile;
        return profile;
    }

    /**
     * Aplica una lectura de LinkedIn. Las secciones editadas a mano y las que no se pudieron leer ({@code read} no
     * las incluye) se conservan.
     */
    public void sync(ProfileContent read, Set<ProfileSection> sectionsRead, Reading reading, Instant now) {
        apply(read, sectionsRead);
        complete = reading.complete();
        missingSections = new ArrayList<>(reading.missingSections());
        lastSyncedAt = now;
        updatedAt = now;
    }

    /** Aplica lo que la IA extrajo de un CV: solo las secciones que traen algo y no se editaron a mano. */
    public void importCv(ProfileContent extracted, Instant now) {
        apply(extracted, extracted.presentSections());
        cvImportedAt = now;
        updatedAt = now;
    }

    /** La búsqueda que eligió la IA y, si no se editó a mano, el perfil laboral que dedujo. */
    public void suggestSearch(SearchSuggestion suggestion, Instant now) {
        searchPreference = searchPreference.suggested(suggestion, now);
        WorkProfile inferred = suggestion.workProfile();
        if (inferred != null && !manuallyEditedFields.contains(ProfileSection.WORK_PROFILE)) {
            workProfile = new WorkProfile(inferred.primaryRole(), inferred.secondaryRoles(), inferred.seniority(),
                    inferred.title(), WorkProfile.Source.AI, now);
        }
        updatedAt = now;
    }

    /** La persona define su perfil laboral: desde ahora la IA no lo cambia. */
    public void defineWorkProfile(WorkProfile defined, Instant now) {
        workProfile = new WorkProfile(defined.primaryRole(), defined.secondaryRoles(), defined.seniority(),
                defined.title(), WorkProfile.Source.MANUAL, now);
        manuallyEditedFields.add(ProfileSection.WORK_PROFILE);
        updatedAt = now;
    }

    public void configureSearch(SearchSettings settings, Instant now) {
        searchPreference = searchPreference.configured(settings.keywords(), settings.remoteOnly(),
                settings.postedWithinDays(), settings.location());
        updatedAt = now;
    }

    private void apply(ProfileContent read, Set<ProfileSection> sections) {
        Set<ProfileSection> copy = EnumSet.noneOf(ProfileSection.class);
        copy.addAll(sections);
        copy.removeAll(manuallyEditedFields);
        ProfileContent c = content;
        content = new ProfileContent(
                copy.contains(ProfileSection.HEADLINE) ? read.headline() : c.headline(),
                copy.contains(ProfileSection.SUMMARY) ? read.summary() : c.summary(),
                copy.contains(ProfileSection.SKILLS) ? read.skills() : c.skills(),
                copy.contains(ProfileSection.EXPERIENCE) ? read.experiences() : c.experiences(),
                copy.contains(ProfileSection.EDUCATION) ? read.education() : c.education(),
                copy.contains(ProfileSection.LANGUAGES) ? read.languages() : c.languages(),
                copy.contains(ProfileSection.CERTIFICATIONS) ? read.certifications() : c.certifications());
    }

    private static Set<ProfileSection> enumSet(Set<ProfileSection> values) {
        return values.isEmpty() ? EnumSet.noneOf(ProfileSection.class) : EnumSet.copyOf(values);
    }

    public ProfileContent content() {
        return content;
    }

    public SyncState syncState() {
        return new SyncState(enumSet(manuallyEditedFields), complete, List.copyOf(missingSections), lastSyncedAt,
                cvImportedAt, updatedAt);
    }

    public SearchPreference searchPreference() {
        return searchPreference;
    }

    /** {@code null} = todavía no se sabe. */
    public WorkProfile workProfile() {
        return workProfile;
    }

    public UUID userId() {
        return userId;
    }

    public Instant createdAt() {
        return createdAt;
    }

    /** Qué se sabe de la última sincronización con LinkedIn y de la última importación de CV. */
    public record SyncState(Set<ProfileSection> manuallyEditedFields, boolean complete, List<String> missingSections,
                            Instant lastSyncedAt, Instant cvImportedAt, Instant updatedAt) {
    }

    /** Si la lectura de LinkedIn fue completa y qué faltó. */
    public record Reading(boolean complete, List<String> missingSections) {
    }

    /** Lo que la persona ajusta de su búsqueda. */
    public record SearchSettings(String keywords, boolean remoteOnly, Integer postedWithinDays, String location) {
    }
}

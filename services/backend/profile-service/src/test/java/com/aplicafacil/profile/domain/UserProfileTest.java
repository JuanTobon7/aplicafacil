package com.aplicafacil.profile.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.YearMonth;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/** Las reglas del perfil viven en el dominio: se prueban sin Spring ni base de datos. */
class UserProfileTest {

    private final Instant now = Instant.parse("2026-10-19T12:00:00Z");

    @Test
    void syncCopiesWhatWasReadAndKeepsTheRest() {
        UserProfile profile = UserProfile.create(UUID.randomUUID(), now);
        profile.sync(content("Resumen", "Java"), EnumSet.allOf(ProfileSection.class), reading(), now);

        profile.sync(content(null, "Go"), EnumSet.of(ProfileSection.SKILLS),
                new UserProfile.Reading(false, List.of("summary")), now.plusSeconds(60));

        assertThat(profile.content().summary()).isEqualTo("Resumen");
        assertThat(profile.content().skills()).extracting(Skill::name).containsExactly("Go");
        assertThat(profile.syncState().missingSections()).containsExactly("summary");
        assertThat(profile.syncState().lastSyncedAt()).isEqualTo(now.plusSeconds(60));
    }

    @Test
    void neverOverwritesManuallyEditedSections() {
        UserProfile profile = UserProfile.restore(UUID.randomUUID(), now, content("Mi resumen"),
                new UserProfile.SyncState(Set.of(ProfileSection.SUMMARY), true, List.of(), now, null, now),
                SearchPreference.NONE, null);

        profile.sync(content("De LinkedIn"), EnumSet.allOf(ProfileSection.class), reading(), now);
        profile.importCv(content("Del CV", "Java"), now);

        assertThat(profile.content().summary()).isEqualTo("Mi resumen");
        assertThat(profile.content().skills()).extracting(Skill::name).containsExactly("Java");
    }

    @Test
    void cvImportOnlyReplacesSectionsItBrings() {
        UserProfile profile = UserProfile.create(UUID.randomUUID(), now);
        profile.sync(content("Resumen", "Java"), EnumSet.allOf(ProfileSection.class), reading(), now);
        Experience job = Experience.of("Backend Developer", "ACME",
                new Period(YearMonth.of(2021, 3), null), "Medellín", "APIs", List.of("Spring Boot"));

        profile.importCv(new ProfileContent("Desarrollador Backend", null, List.of(), List.of(job), null, null, null),
                now.plusSeconds(5));

        ProfileContent c = profile.content();
        assertThat(c.headline()).isEqualTo("Desarrollador Backend");
        assertThat(c.summary()).isEqualTo("Resumen");
        assertThat(c.skills()).extracting(Skill::name).containsExactly("Java");
        assertThat(c.experiences()).containsExactly(job);
        assertThat(profile.syncState().cvImportedAt()).isEqualTo(now.plusSeconds(5));
    }

    @Test
    void repeatedSkillsAndLanguagesAreMergedKeepingTheFirst() {
        ProfileContent c = new ProfileContent(null, null,
                List.of(Skill.named("Java"), Skill.named("java "), Skill.named("Spring")), null, null,
                List.of(Language.of("Inglés", "B2"), Language.of("inglés", "C1")), null);

        assertThat(c.skills()).extracting(Skill::name).containsExactly("Java", "Spring");
        assertThat(c.languages()).extracting(Language::proficiency).containsExactly("B2");
    }

    @Test
    void aSuggestionKeepsWhatThePersonConfigured() {
        UserProfile profile = UserProfile.create(UUID.randomUUID(), now);
        profile.configureSearch(new UserProfile.SearchSettings("java", true, 7, "Bogotá"), now);

        profile.suggestSearch(new SearchSuggestion("desarrollador backend java spring boot",
                List.of("Desarrollador Backend Java Spring Boot", "ingeniero de software java", ""), "Tu fuerte es Java",
                null),
                now.plusSeconds(1));

        SearchPreference search = profile.searchPreference();
        assertThat(search.keywords()).isEqualTo("desarrollador backend java spring boot");
        assertThat(search.alternatives()).containsExactly("ingeniero de software java");
        assertThat(search.remoteOnly()).isTrue();
        assertThat(search.postedWithinDays()).isEqualTo(7);
        assertThat(search.location()).isEqualTo("Bogotá");
        assertThat(search.suggestedAt()).isEqualTo(now.plusSeconds(1));
    }

    @Test
    void searchSettingsAreValidated() {
        UserProfile profile = UserProfile.create(UUID.randomUUID(), now);

        assertThatThrownBy(() -> profile.configureSearch(new UserProfile.SearchSettings("java", false, 60, null), now))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> profile.configureSearch(new UserProfile.SearchSettings(" ", false, null, null), now))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void anAiTaskClosesOnlyOnce() {
        UUID user = UUID.randomUUID();
        UUID config = UUID.randomUUID();
        AiTask task = AiTask.submit(user, config, AiTaskKind.CV_EXTRACTION, "cv.pdf",
                new AiTask.Window(now, now.plusSeconds(180)));

        assertThat(task.belongsTo(user, config)).isTrue();
        assertThat(task.belongsTo(user, UUID.randomUUID())).isFalse();
        task.complete(now.plusSeconds(10));

        assertThat(task.outcome().status()).isEqualTo(AiTaskStatus.COMPLETED);
        assertThatThrownBy(() -> task.fail("LATE", "tarde", now)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void theAiInfersTheWorkProfileUntilThePersonDefinesIt() {
        UserProfile profile = UserProfile.create(UUID.randomUUID(), now);
        profile.suggestSearch(suggestion(new WorkProfile(WorkRole.BACKEND, List.of(WorkRole.FULLSTACK),
                Seniority.JUNIOR, "Desarrollador backend Java", WorkProfile.Source.AI, null)), now);

        assertThat(profile.workProfile().primaryRole()).isEqualTo(WorkRole.BACKEND);
        assertThat(profile.workProfile().source()).isEqualTo(WorkProfile.Source.AI);
        assertThat(profile.workProfile().updatedAt()).isEqualTo(now);

        profile.defineWorkProfile(new WorkProfile(WorkRole.FULLSTACK, List.of(), Seniority.MID, null,
                WorkProfile.Source.AI, null), now.plusSeconds(60));
        profile.suggestSearch(suggestion(new WorkProfile(WorkRole.DATA_SCIENCE, List.of(), null, null,
                WorkProfile.Source.AI, null)), now.plusSeconds(120));

        assertThat(profile.workProfile().primaryRole()).isEqualTo(WorkRole.FULLSTACK);
        assertThat(profile.workProfile().source()).isEqualTo(WorkProfile.Source.MANUAL);
        assertThat(profile.syncState().manuallyEditedFields()).contains(ProfileSection.WORK_PROFILE);
        assertThat(profile.searchPreference().keywords()).isEqualTo("desarrollador java"); // la búsqueda sí cambia
    }

    @Test
    void aWorkProfileOutsideTheCatalogNeedsATitleAndSecondaryRolesAreCleaned() {
        assertThatThrownBy(() -> new WorkProfile(WorkRole.OTHER, List.of(), null, " ", WorkProfile.Source.MANUAL, null))
                .isInstanceOf(IllegalArgumentException.class);
        WorkProfile accountant = new WorkProfile(WorkRole.OTHER, null, Seniority.SENIOR, "Contador público",
                WorkProfile.Source.MANUAL, null);
        assertThat(accountant.title()).isEqualTo("Contador público");

        WorkProfile backend = new WorkProfile(WorkRole.BACKEND,
                List.of(WorkRole.BACKEND, WorkRole.DEVOPS, WorkRole.DEVOPS, WorkRole.FRONTEND), null, null,
                WorkProfile.Source.AI, null);
        assertThat(backend.secondaryRoles()).containsExactly(WorkRole.DEVOPS, WorkRole.FRONTEND);
        assertThatThrownBy(() -> new WorkProfile(WorkRole.BACKEND,
                List.of(WorkRole.QA, WorkRole.DEVOPS, WorkRole.FRONTEND, WorkRole.MOBILE), null, null,
                WorkProfile.Source.MANUAL, null)).isInstanceOf(IllegalArgumentException.class);
    }

    private static SearchSuggestion suggestion(WorkProfile workProfile) {
        return new SearchSuggestion("desarrollador java", List.of(), null, workProfile);
    }

    private static ProfileContent content(String summary, String... skills) {
        return new ProfileContent(null, summary, List.of(skills).stream().map(Skill::named).toList(), null, null,
                null, null);
    }

    private static UserProfile.Reading reading() {
        return new UserProfile.Reading(true, List.of());
    }
}

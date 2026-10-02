package com.aplicafacil.profile.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class ProfileTest {

    private final UUID personId = UUID.randomUUID();

    @Test
    void createsAnEmptyProfileForAPerson() {
        Profile profile = Profile.create(personId, " Backend Developer ", "Java y Spring");

        assertThat(profile.getId()).isNotNull();
        assertThat(profile.getPersonId()).isEqualTo(personId);
        assertThat(profile.getTitle()).isEqualTo("Backend Developer");
        assertThat(profile.getSkills()).isEmpty();
        assertThat(profile.hasCv()).isFalse();
    }

    @Test
    void requiresPersonTitleAndSummary() {
        assertThatThrownBy(() -> Profile.create(null, "Dev", "Resumen"))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("personId");
        assertThatThrownBy(() -> Profile.create(personId, " ", "Resumen"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("title");
        assertThatThrownBy(() -> Profile.create(personId, "Dev", ""))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("summary");
    }

    @Test
    void collectionsAreReadOnlyAndChangeThroughTheAggregate() {
        Profile profile = Profile.create(personId, "Dev", "Resumen");
        Skill java = Skill.create("Java", null, 5);

        profile.addSkill(java);

        assertThatThrownBy(() -> profile.getSkills().add(Skill.create("Go", null, 1)))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(profile.removeSkill(java.getId())).isTrue();
        assertThat(profile.getSkills()).isEmpty();
    }

    @Test
    void rejectsTheSameElementTwice() {
        Profile profile = Profile.create(personId, "Dev", "Resumen");
        Skill java = Skill.create("Java", null, 5);
        profile.addSkill(java);

        assertThatThrownBy(() -> profile.addSkill(java))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void periodsCannotEndBeforeTheyStart() {
        LocalDate start = LocalDate.of(2024, 5, 1);

        assertThatThrownBy(() -> Experience.create("ACME", "Dev", null, start, start.minusDays(1)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("endDate");
        assertThatThrownBy(() -> Education.create("UdeA", null, start, start.minusYears(1)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("endDate");
        assertThat(Experience.create("ACME", "Dev", null, start, null).isCurrent()).isTrue();
    }

    @Test
    void validatesChildLimits() {
        assertThatThrownBy(() -> Skill.create("a".repeat(Skill.MAX_NAME_LENGTH + 1), null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Skill.create("Java", null, -1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Experience.create("ACME", "Dev", "d".repeat(Experience.MAX_DESCRIPTION_LENGTH + 1),
                LocalDate.now(), null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Project.create("API", " ", null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("description");
        assertThatThrownBy(() -> Cv.create("s3://cvs/juan.pdf", null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("mimeType");
    }

    @Test
    void attachingACvReplacesThePreviousOne() {
        Profile profile = Profile.create(personId, "Dev", "Resumen");

        profile.attachCv(Cv.create("cvs/v1.pdf", "application/pdf"));
        profile.attachCv(Cv.create("cvs/v2.pdf", "application/pdf"));

        assertThat(profile.getCv().getLocation()).isEqualTo("cvs/v2.pdf");
        profile.removeCv();
        assertThat(profile.hasCv()).isFalse();
    }
}

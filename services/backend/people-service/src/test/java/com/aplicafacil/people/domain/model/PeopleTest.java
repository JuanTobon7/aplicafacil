package com.aplicafacil.people.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.aplicafacil.people.domain.exception.FieldTooLongException;
import com.aplicafacil.people.domain.exception.InvalidEmailException;
import com.aplicafacil.people.domain.exception.RequiredFieldException;

class PeopleTest {

    @Test
    void createNormalizesEmailAndTrimsNames() {
        People people = People.create(UUID.randomUUID(), "  Juan ", " Tobón ", "  Juan@Example.COM ");

        assertThat(people.getId()).isNotNull();
        assertThat(people.getFirstName()).isEqualTo("Juan");
        assertThat(people.getLastName()).isEqualTo("Tobón");
        assertThat(people.getEmail()).isEqualTo("juan@example.com");
        assertThat(people.getFullName()).isEqualTo("Juan Tobón");
        assertThat(people.getCreatedAt()).isNull();
    }

    @Test
    void requiresNamesAndValidEmail() {
        assertThatThrownBy(() -> People.create(UUID.randomUUID(), " ", "Tobón", "juan@example.com"))
                .isInstanceOf(RequiredFieldException.class).hasMessageContaining("firstName");
        assertThatThrownBy(() -> People.create(UUID.randomUUID(), "Juan", null, "juan@example.com"))
                .isInstanceOf(RequiredFieldException.class).hasMessageContaining("lastName");
        assertThatThrownBy(() -> People.create(null, "Juan", "Tobón", "juan@example.com"))
                .isInstanceOf(RequiredFieldException.class).hasMessageContaining("userId");
        assertThatThrownBy(() -> People.create(UUID.randomUUID(), "Juan", "Tobón", "no-es-un-email"))
                .isInstanceOf(InvalidEmailException.class);
    }

    @Test
    void rejectsNamesLongerThan100Characters() {
        String tooLong = "a".repeat(People.MAX_NAME_LENGTH + 1);

        assertThatThrownBy(() -> People.create(UUID.randomUUID(), tooLong, "Tobón", "juan@example.com"))
                .isInstanceOf(FieldTooLongException.class).hasMessageContaining("100");
    }

    @Test
    void blankContactDetailsBecomeAbsent() {
        People people = People.create(UUID.randomUUID(), "Juan", "Tobón", "juan@example.com");

        people.updateContactDetails(" ", "https://linkedin.com/in/juan", "", null, "https://cv.example.com/juan.pdf");

        assertThat(people.getPhone()).isNull();
        assertThat(people.getLinkedinUrl()).isEqualTo("https://linkedin.com/in/juan");
        assertThat(people.getGithubUrl()).isNull();
        assertThat(people.getPortfolioUrl()).isNull();
        assertThat(people.getResumeUrl()).isEqualTo("https://cv.example.com/juan.pdf");
    }

    @Test
    void belongsToItsUser() {
        UUID userId = UUID.randomUUID();
        People people = People.create(userId, "Juan", "Tobón", "juan@example.com");

        assertThat(people.isOwnedBy(userId)).isTrue();
        assertThat(people.isOwnedBy(UUID.randomUUID())).isFalse();
    }

    @Test
    void equalityIsById() {
        People people = People.create(UUID.randomUUID(), "Juan", "Tobón", "juan@example.com");
        People sameId = new People(people.getId(), UUID.randomUUID(), "Otro", "Nombre", "otro@example.com",
                null, null, null, null, null, null, null);

        assertThat(people).isEqualTo(sameId).hasSameHashCodeAs(sameId);
    }
}

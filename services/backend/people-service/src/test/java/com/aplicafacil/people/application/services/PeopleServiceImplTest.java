package com.aplicafacil.people.application.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.aplicafacil.people.application.dto.PeopleDto;
import com.aplicafacil.people.application.dto.PeopleDtoRegister;
import com.aplicafacil.people.application.exception.EmailAlreadyUsedException;
import com.aplicafacil.people.application.exception.PeopleAccessDeniedException;
import com.aplicafacil.people.application.exception.PeopleAlreadyRegisteredException;
import com.aplicafacil.people.application.exception.PeopleNotFoundException;
import com.aplicafacil.people.application.mapper.PeopleDtoMapperImpl;
import com.aplicafacil.people.domain.exception.InvalidEmailException;

class PeopleServiceImplTest {

    private final InMemoryPeopleRepository repository = new InMemoryPeopleRepository();
    private final PeopleServiceImpl service = new PeopleServiceImpl(repository, new PeopleDtoMapperImpl());

    @Test
    void registersAndReadsThePeopleOfAUser() {
        UUID userId = UUID.randomUUID();

        PeopleDto created = service.registerPeople(userId, register("Juan", "Juan@Example.com", "+57 300"));

        assertThat(created.getUserId()).isEqualTo(userId);
        assertThat(created.getEmail()).isEqualTo("juan@example.com");
        assertThat(created.getPhone()).isEqualTo("+57 300");
        assertThat(service.getPeople(created.getId(), owner(created)).getFirstName()).isEqualTo("Juan");
        assertThat(service.getPeopleByUserId(userId).getId()).isEqualTo(created.getId());
    }

    @Test
    void aUserRegistersASinglePeople() {
        UUID userId = UUID.randomUUID();
        service.registerPeople(userId, register("Juan", "juan@example.com", null));

        assertThatThrownBy(() -> service.registerPeople(userId, register("Juan", "otro@example.com", null)))
                .isInstanceOf(PeopleAlreadyRegisteredException.class);
    }

    @Test
    void registerRejectsAnEmailInUse() {
        service.registerPeople(UUID.randomUUID(), register("Juan", "juan@example.com", null));

        assertThatThrownBy(() -> service.registerPeople(UUID.randomUUID(), register("Ana", "JUAN@example.com", null)))
                .isInstanceOf(EmailAlreadyUsedException.class);
    }

    @Test
    void updateReplacesDataAndKeepsOwnEmail() {
        PeopleDto created = service.registerPeople(UUID.randomUUID(), register("Juan", "juan@example.com", "+57 300"));

        PeopleDto updated = service.updatePeople(created.getId(), register("Juan Carlos", "juan@example.com", null),
                owner(created));

        assertThat(updated.getFirstName()).isEqualTo("Juan Carlos");
        assertThat(updated.getPhone()).isNull();
        assertThat(updated.getUserId()).isEqualTo(created.getUserId());
    }

    @Test
    void updateRejectsAnEmailOfAnotherPeople() {
        service.registerPeople(UUID.randomUUID(), register("Ana", "ana@example.com", null));
        PeopleDto juan = service.registerPeople(UUID.randomUUID(), register("Juan", "juan@example.com", null));

        assertThatThrownBy(() -> service.updatePeople(juan.getId(), register("Juan", "ana@example.com", null), owner(juan)))
                .isInstanceOf(EmailAlreadyUsedException.class);
    }

    @Test
    void invalidDataIsRejectedByTheDomain() {
        assertThatThrownBy(() -> service.registerPeople(UUID.randomUUID(), register("Juan", "no-es-un-email", null)))
                .isInstanceOf(InvalidEmailException.class);
    }

    @Test
    void deleteRemovesThePeople() {
        PeopleDto created = service.registerPeople(UUID.randomUUID(), register("Juan", "juan@example.com", null));

        service.deletePeople(created.getId(), owner(created));

        assertThat(repository.byId).isEmpty();
        assertThatThrownBy(() -> service.getPeople(created.getId(), owner(created)))
                .isInstanceOf(PeopleNotFoundException.class);
    }

    @Test
    void anotherUserCannotReadUpdateOrDeleteThePeople() {
        PeopleDto juan = service.registerPeople(UUID.randomUUID(), register("Juan", "juan@example.com", null));
        UUID stranger = UUID.randomUUID();

        assertThatThrownBy(() -> service.getPeople(juan.getId(), stranger))
                .isInstanceOf(PeopleAccessDeniedException.class);
        assertThatThrownBy(() -> service.updatePeople(juan.getId(), register("Hack", "hack@example.com", null), stranger))
                .isInstanceOf(PeopleAccessDeniedException.class);
        assertThatThrownBy(() -> service.deletePeople(juan.getId(), stranger))
                .isInstanceOf(PeopleAccessDeniedException.class);
        assertThat(repository.byId.get(juan.getId()).getFirstName()).isEqualTo("Juan");
    }

    @Test
    void unknownIdsAreNotFound() {
        UUID unknown = UUID.randomUUID();

        assertThatThrownBy(() -> service.getPeople(unknown, unknown)).isInstanceOf(PeopleNotFoundException.class);
        assertThatThrownBy(() -> service.getPeopleByUserId(unknown)).isInstanceOf(PeopleNotFoundException.class);
        assertThatThrownBy(() -> service.updatePeople(unknown, register("Juan", "juan@example.com", null), unknown))
                .isInstanceOf(PeopleNotFoundException.class);
        assertThatThrownBy(() -> service.deletePeople(unknown, unknown)).isInstanceOf(PeopleNotFoundException.class);
    }

    private static UUID owner(PeopleDto people) {
        return people.getUserId();
    }

    private static PeopleDtoRegister register(String firstName, String email, String phone) {
        return new PeopleDtoRegister(firstName, "Tobón", email, phone, null, null, null, null);
    }
}

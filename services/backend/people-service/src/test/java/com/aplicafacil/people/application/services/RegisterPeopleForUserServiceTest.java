package com.aplicafacil.people.application.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.aplicafacil.people.application.dto.PeopleDto;
import com.aplicafacil.people.application.dto.RegisterPeopleForUserCommand;
import com.aplicafacil.people.application.exception.EmailAlreadyUsedException;
import com.aplicafacil.people.application.mapper.PeopleDtoMapperImpl;

class RegisterPeopleForUserServiceTest {

    private final InMemoryPeopleRepository repository = new InMemoryPeopleRepository();
    private final RegisterPeopleForUserService service =
            new RegisterPeopleForUserService(repository, new PeopleDtoMapperImpl());

    @Test
    void createsThePeopleOfANewUser() {
        UUID userId = UUID.randomUUID();

        PeopleDto people = service.register(new RegisterPeopleForUserCommand(userId, "Juan@Example.com", "Juan", "Tobón"));

        assertThat(people.getUserId()).isEqualTo(userId);
        assertThat(people.getEmail()).isEqualTo("juan@example.com");
        assertThat(repository.byId).hasSize(1);
    }

    @Test
    void isIdempotentForRepeatedEvents() {
        var command = new RegisterPeopleForUserCommand(UUID.randomUUID(), "juan@example.com", "Juan", "Tobón");

        PeopleDto first = service.register(command);
        PeopleDto second = service.register(command);

        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(repository.byId).hasSize(1);
    }

    @Test
    void rejectsAnEmailUsedByAnotherUser() {
        service.register(new RegisterPeopleForUserCommand(UUID.randomUUID(), "juan@example.com", "Juan", "Tobón"));

        assertThatThrownBy(() -> service.register(
                new RegisterPeopleForUserCommand(UUID.randomUUID(), "JUAN@example.com", "Otro", "Juan")))
                .isInstanceOf(EmailAlreadyUsedException.class);
    }
}

package com.aplicafacil.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.aplicafacil.auth.application.exception.EmailAlreadyRegisteredException;
import com.aplicafacil.auth.application.port.PasswordHasher;
import com.aplicafacil.auth.application.port.UserRepository;
import com.aplicafacil.auth.domain.Role;
import com.aplicafacil.auth.domain.User;
import com.aplicafacil.auth.domain.UserRegistered;

class RegisterUserUseCaseTest {

    private final InMemoryUsers users = new InMemoryUsers();
    private final List<UserRegistered> published = new ArrayList<>();
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-01T12:00:00Z"), ZoneOffset.UTC);
    private final RegisterUserUseCase useCase = new RegisterUserUseCase(
            users, new FakeHasher(), published::add, clock);

    @Test
    void registersTheUserAndPublishesTheEvent() {
        User user = useCase.register(RegisterUserUseCase.Command.withUserRole(
                "Juan@Example.com", "Segura123", " Juan ", "Tobón"));

        assertThat(user.getRoles()).containsExactly(Role.USER);
        assertThat(user.getPasswordHash()).isEqualTo("hashed:Segura123");
        assertThat(published).singleElement().satisfies(event -> {
            assertThat(event.userId()).isEqualTo(user.getId());
            assertThat(event.email()).isEqualTo("juan@example.com");
            assertThat(event.firstName()).isEqualTo("Juan");
            assertThat(event.occurredAt()).isEqualTo(clock.instant());
        });
    }

    @Test
    void rejectsADuplicatedEmailIgnoringCase() {
        useCase.register(RegisterUserUseCase.Command.withUserRole("juan@example.com", "Segura123", "Juan", "Tobón"));

        assertThatThrownBy(() -> useCase.register(
                RegisterUserUseCase.Command.withUserRole("JUAN@example.com", "Segura123", "Otro", "Juan")))
                .isInstanceOf(EmailAlreadyRegisteredException.class);
        assertThat(published).hasSize(1);
    }

    @Test
    void appliesThePasswordPolicyBeforeSaving() {
        assertThatThrownBy(() -> useCase.register(
                RegisterUserUseCase.Command.withUserRole("juan@example.com", "corta", "Juan", "Tobón")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(users.byId).isEmpty();
        assertThat(published).isEmpty();
    }

    @Test
    void requiresNames() {
        assertThatThrownBy(() -> useCase.register(
                RegisterUserUseCase.Command.withUserRole("juan@example.com", "Segura123", " ", "Tobón")))
                .hasMessageContaining("firstName");
    }

    private static final class FakeHasher implements PasswordHasher {
        @Override
        public String hash(String rawPassword) {
            return "hashed:" + rawPassword;
        }

        @Override
        public boolean matches(String rawPassword, String passwordHash) {
            return passwordHash.equals(hash(rawPassword));
        }
    }

    private static final class InMemoryUsers implements UserRepository {

        final Map<UUID, User> byId = new HashMap<>();

        @Override
        public User save(User user) {
            byId.put(user.getId(), user);
            return user;
        }

        @Override
        public Optional<User> findById(UUID id) {
            return Optional.ofNullable(byId.get(id));
        }

        @Override
        public Optional<User> findByEmail(String email) {
            return byId.values().stream().filter(u -> u.getEmail().equalsIgnoreCase(email.strip())).findFirst();
        }

        @Override
        public boolean existsByEmail(String email) {
            return findByEmail(email).isPresent();
        }

        @Override
        public List<User> findPage(int page, int size) {
            return byId.values().stream().skip((long) page * size).limit(size).toList();
        }

        @Override
        public long count() {
            return byId.size();
        }
    }
}

package com.aplicafacil.auth.application;

import java.time.Clock;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aplicafacil.auth.application.exception.EmailAlreadyRegisteredException;
import com.aplicafacil.auth.application.port.PasswordHasher;
import com.aplicafacil.auth.application.port.UserEventPublisher;
import com.aplicafacil.auth.application.port.UserRepository;
import com.aplicafacil.auth.domain.PasswordPolicy;
import com.aplicafacil.auth.domain.Role;
import com.aplicafacil.auth.domain.User;
import com.aplicafacil.auth.domain.UserRegistered;

/**
 * Registro de una cuenta nueva. Guarda el usuario y avisa a people-service
 * (evento {@link UserRegistered}) para que cree la persona con nombre y apellido.
 */
@Service
public class RegisterUserUseCase {

    private final UserRepository users;
    private final PasswordHasher passwordHasher;
    private final UserEventPublisher events;
    private final Clock clock;

    public RegisterUserUseCase(UserRepository users, PasswordHasher passwordHasher,
                               UserEventPublisher events, Clock clock) {
        this.users = users;
        this.passwordHasher = passwordHasher;
        this.events = events;
        this.clock = clock;
    }

    public record Command(String email, String password, String firstName, String lastName, Set<Role> roles) {

        /** Registro público: siempre con rol USER. */
        public static Command withUserRole(String email, String password, String firstName, String lastName) {
            return new Command(email, password, firstName, lastName, Set.of(Role.USER));
        }
    }

    @Transactional
    public User register(Command command) {
        String email = User.normalizeEmail(command.email());
        requireName(command.firstName(), "firstName");
        requireName(command.lastName(), "lastName");
        PasswordPolicy.validate(command.password(), email);
        if (users.existsByEmail(email)) {
            throw new EmailAlreadyRegisteredException(email);
        }

        User user = users.save(User.register(email, passwordHasher.hash(command.password()), command.roles()));
        events.publish(new UserRegistered(UUID.randomUUID(), user.getId(), user.getEmail(),
                command.firstName().strip(), command.lastName().strip(), clock.instant()));
        return user;
    }

    /** people-service valida los nombres; aquí solo se evita publicar un evento inservible. */
    private static void requireName(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " es obligatorio");
        }
        if (value.strip().length() > 100) {
            throw new IllegalArgumentException(field + " no puede superar 100 caracteres");
        }
    }
}

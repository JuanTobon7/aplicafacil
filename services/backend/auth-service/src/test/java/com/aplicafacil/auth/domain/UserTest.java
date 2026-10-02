package com.aplicafacil.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Set;

import org.junit.jupiter.api.Test;

class UserTest {

    private final Instant now = Instant.parse("2026-10-01T12:00:00Z");

    @Test
    void registerNormalizesEmailAndStartsEnabled() {
        User user = User.register("  Juan@Example.COM ", "{bcrypt}hash", Set.of(Role.USER));

        assertThat(user.getId()).isNotNull();
        assertThat(user.getEmail()).isEqualTo("juan@example.com");
        assertThat(user.isEnabled()).isTrue();
        assertThat(user.hasRole(Role.USER)).isTrue();
        assertThat(user.hasRole(Role.ADMIN)).isFalse();
    }

    @Test
    void requiresAtLeastOneRoleAndAValidEmail() {
        assertThatThrownBy(() -> User.register("juan@example.com", "{bcrypt}hash", Set.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> User.register("no-es-email", "{bcrypt}hash", Set.of(Role.USER)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("email");
    }

    @Test
    void locksAfterTooManyFailedLoginsAndUnlocksLater() {
        User user = User.register("juan@example.com", "{bcrypt}hash", Set.of(Role.USER));

        for (int i = 0; i < User.MAX_FAILED_ATTEMPTS - 1; i++) {
            user.recordFailedLogin(now);
        }
        assertThat(user.isLocked(now)).isFalse();

        user.recordFailedLogin(now);
        assertThat(user.isLocked(now)).isTrue();
        assertThat(user.isLocked(now.plus(User.LOCK_DURATION))).isFalse();
    }

    @Test
    void successfulLoginResetsTheCounter() {
        User user = User.register("juan@example.com", "{bcrypt}hash", Set.of(Role.USER));
        user.recordFailedLogin(now);
        user.recordFailedLogin(now);

        user.recordSuccessfulLogin(now);

        assertThat(user.getFailedLoginAttempts()).isZero();
        assertThat(user.getLastLoginAt()).isEqualTo(now);
    }

    @Test
    void rolesCannotBeModifiedFromOutside() {
        User user = User.register("juan@example.com", "{bcrypt}hash", Set.of(Role.USER));

        assertThatThrownBy(() -> user.getRoles().add(Role.ADMIN))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void passwordPolicy() {
        PasswordPolicy.validate("Segura123", "juan@example.com");

        assertThatThrownBy(() -> PasswordPolicy.validate("Corta1", "juan@example.com"))
                .hasMessageContaining("al menos");
        assertThatThrownBy(() -> PasswordPolicy.validate("solamenteletras", "juan@example.com"))
                .hasMessageContaining("letras y números");
        assertThatThrownBy(() -> PasswordPolicy.validate("x".repeat(70) + "12345", "juan@example.com"))
                .hasMessageContaining("superar");
        assertThatThrownBy(() -> PasswordPolicy.validate("juan@example.com1", "juan@example.com"))
                .hasMessageContaining("email");
    }
}

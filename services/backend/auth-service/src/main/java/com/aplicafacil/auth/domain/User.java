package com.aplicafacil.auth.domain;

import static com.aplicafacil.auth.domain.DomainValidation.requireNonNull;
import static com.aplicafacil.auth.domain.DomainValidation.requireText;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Cuenta de usuario (identidad). Su {@code id} es el {@code sub} de los tokens.
 *
 * <p>Los datos personales (nombre, teléfono, links) no viven aquí sino en
 * people-service, que crea la {@code Person} al recibir {@link UserRegistered}.
 *
 * <p>Protección contra fuerza bruta: tras {@link #MAX_FAILED_ATTEMPTS} intentos
 * fallidos seguidos la cuenta se bloquea {@link #LOCK_DURATION}.
 */
public final class User {

    public static final int MAX_EMAIL_LENGTH = 255;
    public static final int MAX_FAILED_ATTEMPTS = 5;
    public static final Duration LOCK_DURATION = Duration.ofMinutes(15);

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final UUID id;
    private final String email;
    private String passwordHash;
    private final Set<Role> roles;
    private boolean enabled;
    private int failedLoginAttempts;
    private Instant lockedUntil;
    private Instant lastLoginAt;
    private final Instant createdAt;
    private final Instant updatedAt;

    /** Reconstruye un usuario existente (persistencia / mappers). */
    public User(UUID id, String email, String passwordHash, Set<Role> roles, boolean enabled,
                int failedLoginAttempts, Instant lockedUntil, Instant lastLoginAt,
                Instant createdAt, Instant updatedAt) {
        this.id = requireNonNull(id, "id");
        this.email = normalizeEmail(email);
        this.passwordHash = requireText(passwordHash, "passwordHash", Integer.MAX_VALUE);
        if (roles == null || roles.isEmpty()) {
            throw new IllegalArgumentException("Un usuario debe tener al menos un rol");
        }
        this.roles = EnumSet.copyOf(roles);
        if (failedLoginAttempts < 0) {
            throw new IllegalArgumentException("failedLoginAttempts no puede ser negativo");
        }
        this.enabled = enabled;
        this.failedLoginAttempts = failedLoginAttempts;
        this.lockedUntil = lockedUntil;
        this.lastLoginAt = lastLoginAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /**
     * Registra una cuenta nueva y habilitada.
     *
     * @param passwordHash hash ya calculado; la política de contraseñas se valida antes
     *                     con {@link PasswordPolicy} (sobre el texto plano).
     */
    public static User register(String email, String passwordHash, Set<Role> roles) {
        return new User(UUID.randomUUID(), email, passwordHash, roles, true, 0, null, null, null, null);
    }

    /** Email normalizado (minúsculas, sin espacios). Es el identificador de login. */
    public static String normalizeEmail(String email) {
        String normalized = requireText(email, "email", MAX_EMAIL_LENGTH).toLowerCase(Locale.ROOT);
        if (!EMAIL.matcher(normalized).matches()) {
            throw new IllegalArgumentException("email no tiene un formato válido");
        }
        return normalized;
    }

    public void changePassword(String newPasswordHash) {
        this.passwordHash = requireText(newPasswordHash, "passwordHash", Integer.MAX_VALUE);
    }

    public void enable() {
        this.enabled = true;
    }

    public void disable() {
        this.enabled = false;
    }

    public boolean hasRole(Role role) {
        return roles.contains(role);
    }

    // ── Login ───────────────────────────────────────────────────────────
    public boolean isLocked(Instant now) {
        return lockedUntil != null && now.isBefore(lockedUntil);
    }

    /** Un login correcto reinicia el contador y levanta cualquier bloqueo. */
    public void recordSuccessfulLogin(Instant now) {
        this.failedLoginAttempts = 0;
        this.lockedUntil = null;
        this.lastLoginAt = requireNonNull(now, "now");
    }

    /** Cuenta un intento fallido; al llegar al máximo bloquea la cuenta temporalmente. */
    public void recordFailedLogin(Instant now) {
        requireNonNull(now, "now");
        if (isLocked(now)) {
            return;
        }
        this.failedLoginAttempts++;
        if (failedLoginAttempts >= MAX_FAILED_ATTEMPTS) {
            this.lockedUntil = now.plus(LOCK_DURATION);
            this.failedLoginAttempts = 0;
        }
    }

    // ── Lectura ─────────────────────────────────────────────────────────
    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public Set<Role> getRoles() {
        return Collections.unmodifiableSet(roles);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public int getFailedLoginAttempts() {
        return failedLoginAttempts;
    }

    public Instant getLockedUntil() {
        return lockedUntil;
    }

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public boolean equals(Object other) {
        return this == other || (other instanceof User user && id.equals(user.id));
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}

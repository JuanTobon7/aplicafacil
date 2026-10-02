package com.aplicafacil.auth.domain;

import java.util.Locale;

/**
 * Reglas para contraseñas en texto plano, antes de hashearlas.
 * El dominio nunca guarda la contraseña: solo su hash.
 */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 8;
    /** BCrypt solo usa los primeros 72 bytes; más largo no aporta y permite abuso de CPU. */
    public static final int MAX_LENGTH = 72;

    private PasswordPolicy() {
    }

    /** @throws IllegalArgumentException con un mensaje mostrable al usuario. */
    public static void validate(String rawPassword, String email) {
        if (rawPassword == null || rawPassword.length() < MIN_LENGTH) {
            throw new IllegalArgumentException("La contraseña debe tener al menos " + MIN_LENGTH + " caracteres");
        }
        if (rawPassword.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("La contraseña no puede superar " + MAX_LENGTH + " caracteres");
        }
        if (rawPassword.chars().noneMatch(Character::isLetter) || rawPassword.chars().noneMatch(Character::isDigit)) {
            throw new IllegalArgumentException("La contraseña debe combinar letras y números");
        }
        if (email != null && rawPassword.toLowerCase(Locale.ROOT).contains(email.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("La contraseña no puede contener el email");
        }
    }
}

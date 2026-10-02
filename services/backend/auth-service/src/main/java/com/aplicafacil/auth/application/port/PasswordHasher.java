package com.aplicafacil.auth.application.port;

/** Hash de contraseñas. La implementación decide el algoritmo (BCrypt por defecto). */
public interface PasswordHasher {

    String hash(String rawPassword);

    boolean matches(String rawPassword, String passwordHash);
}

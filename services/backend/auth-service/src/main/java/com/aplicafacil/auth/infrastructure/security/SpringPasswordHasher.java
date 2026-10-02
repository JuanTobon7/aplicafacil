package com.aplicafacil.auth.infrastructure.security;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.aplicafacil.auth.application.port.PasswordHasher;

/** Adaptador del puerto {@link PasswordHasher} sobre el {@link PasswordEncoder} de Spring (BCrypt). */
@Component
class SpringPasswordHasher implements PasswordHasher {

    private final PasswordEncoder encoder;

    SpringPasswordHasher(PasswordEncoder encoder) {
        this.encoder = encoder;
    }

    @Override
    public String hash(String rawPassword) {
        return encoder.encode(rawPassword);
    }

    @Override
    public boolean matches(String rawPassword, String passwordHash) {
        return encoder.matches(rawPassword, passwordHash);
    }
}

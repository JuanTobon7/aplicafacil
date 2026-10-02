package com.aplicafacil.auth.application;

import java.time.Clock;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aplicafacil.auth.application.port.UserRepository;

/** Registra el resultado de cada login (contador de fallos, bloqueo temporal, último acceso). */
@Service
@Transactional
public class LoginAttemptService {

    private final UserRepository users;
    private final Clock clock;

    public LoginAttemptService(UserRepository users, Clock clock) {
        this.users = users;
        this.clock = clock;
    }

    public void recordSuccess(UUID userId) {
        users.findById(userId).ifPresent(user -> {
            user.recordSuccessfulLogin(clock.instant());
            users.save(user);
        });
    }

    /** Emails inexistentes se ignoran (no se revela si la cuenta existe). */
    public void recordFailure(String email) {
        if (email == null || email.isBlank()) {
            return;
        }
        users.findByEmail(email).ifPresent(user -> {
            user.recordFailedLogin(clock.instant());
            users.save(user);
        });
    }
}

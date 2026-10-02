package com.aplicafacil.auth.application;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aplicafacil.auth.application.exception.InvalidCredentialsException;
import com.aplicafacil.auth.application.exception.UserNotFoundException;
import com.aplicafacil.auth.application.port.PasswordHasher;
import com.aplicafacil.auth.application.port.UserRepository;
import com.aplicafacil.auth.domain.PasswordPolicy;
import com.aplicafacil.auth.domain.User;

/** Operaciones del usuario sobre su propia cuenta. */
@Service
@Transactional(readOnly = true)
public class AccountService {

    private final UserRepository users;
    private final PasswordHasher passwordHasher;

    public AccountService(UserRepository users, PasswordHasher passwordHasher) {
        this.users = users;
        this.passwordHasher = passwordHasher;
    }

    public User getAccount(UUID userId) {
        return users.findById(userId).orElseThrow(() -> new UserNotFoundException(userId));
    }

    /** Exige la contraseña actual: un token robado no basta para tomar la cuenta. */
    @Transactional
    public void changePassword(UUID userId, String currentPassword, String newPassword) {
        User user = getAccount(userId);
        if (currentPassword == null || !passwordHasher.matches(currentPassword, user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        PasswordPolicy.validate(newPassword, user.getEmail());
        user.changePassword(passwordHasher.hash(newPassword));
        users.save(user);
    }
}

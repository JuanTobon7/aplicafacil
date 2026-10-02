package com.aplicafacil.auth.application;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aplicafacil.auth.application.exception.UserNotFoundException;
import com.aplicafacil.auth.application.port.UserRepository;
import com.aplicafacil.auth.domain.User;

/** Administración de cuentas (solo ADMIN; la autorización se aplica en la API). */
@Service
@Transactional(readOnly = true)
public class UserAdministrationService {

    public static final int MAX_PAGE_SIZE = 100;

    private final UserRepository users;

    public UserAdministrationService(UserRepository users) {
        this.users = users;
    }

    public record Page(List<User> users, int page, int size, long total) {
    }

    public Page list(int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.clamp(size, 1, MAX_PAGE_SIZE);
        return new Page(users.findPage(safePage, safeSize), safePage, safeSize, users.count());
    }

    /** Deshabilitar impide nuevos logins; los tokens ya emitidos expiran solos (TTL corto). */
    @Transactional
    public User setEnabled(UUID userId, boolean enabled) {
        User user = users.findById(userId).orElseThrow(() -> new UserNotFoundException(userId));
        if (enabled) {
            user.enable();
        } else {
            user.disable();
        }
        return users.save(user);
    }
}

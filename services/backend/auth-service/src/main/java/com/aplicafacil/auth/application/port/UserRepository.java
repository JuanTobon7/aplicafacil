package com.aplicafacil.auth.application.port;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.aplicafacil.auth.domain.User;

/** Puerto de persistencia de usuarios. */
public interface UserRepository {

    User save(User user);

    Optional<User> findById(UUID id);

    /** El email se normaliza antes de buscar. */
    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    /** Página {@code page} (desde 0) ordenada por fecha de creación. */
    List<User> findPage(int page, int size);

    long count();
}

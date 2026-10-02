package com.aplicafacil.auth.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.aplicafacil.auth.application.port.UserRepository;
import com.aplicafacil.auth.domain.User;
import com.aplicafacil.auth.infrastructure.persistence.mapper.UserPersistenceMapper;

/** Implementación JPA del puerto {@link UserRepository}. */
@Repository
@Transactional(readOnly = true)
class UserRepositoryAdapter implements UserRepository {

    private final SpringDataUserRepository jpa;
    private final UserPersistenceMapper mapper;

    UserRepositoryAdapter(SpringDataUserRepository jpa, UserPersistenceMapper mapper) {
        this.jpa = jpa;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public User save(User user) {
        return mapper.toDomain(jpa.saveAndFlush(mapper.toEntity(user)));
    }

    @Override
    public Optional<User> findById(UUID id) {
        return jpa.findById(id).map(mapper::toDomain);
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return normalize(email).flatMap(jpa::findByEmail).map(mapper::toDomain);
    }

    @Override
    public boolean existsByEmail(String email) {
        return normalize(email).map(jpa::existsByEmail).orElse(false);
    }

    @Override
    public List<User> findPage(int page, int size) {
        return jpa.findAll(PageRequest.of(page, size, Sort.by("createdAt", "id")))
                .map(mapper::toDomain)
                .getContent();
    }

    @Override
    public long count() {
        return jpa.count();
    }

    /** Un email con formato inválido no puede existir: se trata como "no encontrado". */
    private static Optional<String> normalize(String email) {
        try {
            return Optional.of(User.normalizeEmail(email));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}

package com.aplicafacil.people.persistence;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.aplicafacil.people.application.port.out.PeopleRepository;
import com.aplicafacil.people.domain.model.People;
import com.aplicafacil.people.persistence.mapper.PeoplePersistenceMapper;

/** Adaptador de salida: implementación JPA del puerto {@link PeopleRepository}. */
@Repository
@Transactional(readOnly = true)
class PeopleRepositoryAdapter implements PeopleRepository {

    private final PeopleJpaRepository jpa;
    private final PeoplePersistenceMapper mapper;

    PeopleRepositoryAdapter(PeopleJpaRepository jpa, PeoplePersistenceMapper mapper) {
        this.jpa = jpa;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public People save(People people) {
        // flush: así los timestamps generados por Hibernate vuelven en el resultado
        return mapper.toDomain(jpa.saveAndFlush(mapper.toEntity(people)));
    }

    @Override
    public Optional<People> findById(UUID id) {
        return jpa.findById(id).map(mapper::toDomain);
    }

    @Override
    public Optional<People> findByUserId(UUID userId) {
        return jpa.findByUserId(userId).map(mapper::toDomain);
    }

    @Override
    public boolean existsByUserId(UUID userId) {
        return jpa.existsByUserId(userId);
    }

    @Override
    public Optional<People> findByEmail(String email) {
        return jpa.findByEmail(normalize(email)).map(mapper::toDomain);
    }

    @Override
    public boolean existsByEmail(String email) {
        return jpa.existsByEmail(normalize(email));
    }

    @Override
    @Transactional
    public void deleteById(UUID id) {
        jpa.deleteById(id);
    }

    private static String normalize(String email) {
        return email == null ? null : email.strip().toLowerCase(Locale.ROOT);
    }
}

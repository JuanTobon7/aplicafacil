package com.aplicafacil.profile.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.aplicafacil.profile.application.port.ProfileRepository;
import com.aplicafacil.profile.domain.Profile;
import com.aplicafacil.profile.infrastructure.persistence.mapper.ProfilePersistenceMapper;

/**
 * Implementación JPA del puerto {@link ProfileRepository}.
 *
 * <p>El mapeo a dominio ocurre dentro de la transacción: las colecciones son
 * LAZY y se cargan al recorrerlas (por lotes, sin N+1 por perfil).
 */
@Repository
@Transactional(readOnly = true)
class ProfileRepositoryAdapter implements ProfileRepository {

    private final SpringDataProfileRepository jpa;
    private final ProfilePersistenceMapper mapper;

    ProfileRepositoryAdapter(SpringDataProfileRepository jpa, ProfilePersistenceMapper mapper) {
        this.jpa = jpa;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public Profile save(Profile profile) {
        // merge del agregado completo: hijos nuevos se insertan, los que ya no están se borran
        return mapper.toDomain(jpa.saveAndFlush(mapper.toEntity(profile)));
    }

    @Override
    public Optional<Profile> findById(UUID id) {
        return jpa.findById(id).map(mapper::toDomain);
    }

    @Override
    public List<Profile> findByPersonId(UUID personId) {
        return jpa.findByPersonId(personId).stream().map(mapper::toDomain).toList();
    }

    @Override
    @Transactional
    public void deleteById(UUID id) {
        jpa.deleteById(id);
    }
}

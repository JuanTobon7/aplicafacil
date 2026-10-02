package com.aplicafacil.people.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.aplicafacil.people.persistence.entity.PeopleEntity;

/** Spring Data (DAO). Solo lo usa {@link PeopleRepositoryAdapter}. */
interface PeopleJpaRepository extends JpaRepository<PeopleEntity, UUID> {

    Optional<PeopleEntity> findByUserId(UUID userId);

    boolean existsByUserId(UUID userId);

    Optional<PeopleEntity> findByEmail(String email);

    boolean existsByEmail(String email);
}

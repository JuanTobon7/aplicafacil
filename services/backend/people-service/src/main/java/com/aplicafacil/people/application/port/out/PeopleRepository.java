package com.aplicafacil.people.application.port.out;

import java.util.Optional;
import java.util.UUID;

import com.aplicafacil.people.domain.model.People;

/** Puerto de salida: persistencia de personas. Lo implementa el adaptador de persistence. */
public interface PeopleRepository {

    /** Inserta o actualiza. Devuelve la persona con los timestamps asignados. */
    People save(People people);

    Optional<People> findById(UUID id);

    /** Persona de una cuenta de auth-service ({@code sub} del token). */
    Optional<People> findByUserId(UUID userId);

    boolean existsByUserId(UUID userId);

    /** Búsqueda insensible a mayúsculas (los emails se guardan normalizados). */
    Optional<People> findByEmail(String email);

    boolean existsByEmail(String email);

    void deleteById(UUID id);
}

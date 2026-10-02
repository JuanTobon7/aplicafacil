package com.aplicafacil.profile.application.port;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.aplicafacil.profile.domain.Profile;

/**
 * Puerto de persistencia del agregado {@link Profile}. Se guarda y se lee
 * completo (con skills, experiencias, educación, proyectos y CV).
 */
public interface ProfileRepository {

    /** Inserta o actualiza el agregado completo; los hijos que ya no estén se eliminan. */
    Profile save(Profile profile);

    Optional<Profile> findById(UUID id);

    List<Profile> findByPersonId(UUID personId);

    /** Elimina el perfil y todo su contenido (skills, experiencias, educación, proyectos y CV). */
    void deleteById(UUID id);
}

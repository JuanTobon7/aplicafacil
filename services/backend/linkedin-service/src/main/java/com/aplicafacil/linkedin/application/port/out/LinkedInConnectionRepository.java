package com.aplicafacil.linkedin.application.port.out;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.aplicafacil.linkedin.domain.LinkedInConnection;

/** Estado persistente de las conexiones ({@code linkedin_connection}). */
public interface LinkedInConnectionRepository {

    Optional<LinkedInConnection> findById(UUID userId);

    List<LinkedInConnection> findAllById(Collection<UUID> userIds);

    LinkedInConnection save(LinkedInConnection connection);

    /**
     * El miembro pudo estar asociado a otro usuario (cuenta borrada y recreada): se le quita para liberar la
     * restricción única antes de asignarlo a {@code newOwner}.
     */
    void releaseMember(String memberUrn, UUID newOwner);

    void deleteById(UUID userId);
}

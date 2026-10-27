package com.aplicafacil.profile.application.port.out;

import java.util.Optional;
import java.util.UUID;

import com.aplicafacil.profile.domain.UserProfile;

/** Dónde viven los perfiles. La implementación está en infrastructure (DIP). */
public interface UserProfileRepository {

    Optional<UserProfile> findById(UUID userId);

    UserProfile save(UserProfile profile);

    /** Idempotente. */
    void deleteById(UUID userId);
}

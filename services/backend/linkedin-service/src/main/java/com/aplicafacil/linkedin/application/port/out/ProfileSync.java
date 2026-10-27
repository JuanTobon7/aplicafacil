package com.aplicafacil.linkedin.application.port.out;

import java.util.UUID;

import com.aplicafacil.linkedin.contract.ProfileSnapshot;

/** Envía lo leído de LinkedIn a profile-service, que respeta las secciones editadas a mano. */
public interface ProfileSync {

    void sync(UUID userId, ProfileSnapshot snapshot);
}

package com.aplicafacil.profile.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aplicafacil.linkedin.contract.ProfileSnapshot;
import com.aplicafacil.profile.application.port.out.AiTaskRepository;
import com.aplicafacil.profile.application.port.out.UserProfileRepository;
import com.aplicafacil.profile.domain.UserProfile;

/** Casos de uso del perfil. La regla de qué se copia de una lectura está en {@link UserProfile}. */
@Service
public class ProfileSyncService {

    private final UserProfileRepository profiles;
    private final AiTaskRepository tasks;
    private final Clock clock;

    public ProfileSyncService(UserProfileRepository profiles, AiTaskRepository tasks, Clock clock) {
        this.profiles = profiles;
        this.tasks = tasks;
        this.clock = clock;
    }

    @Transactional
    public UserProfile sync(UUID userId, ProfileSnapshot snapshot) {
        Instant now = clock.instant();
        UserProfile profile = profiles.findById(userId).orElseGet(() -> UserProfile.create(userId, now));
        profile.sync(LinkedInSnapshots.content(snapshot), LinkedInSnapshots.sectionsRead(snapshot),
                new UserProfile.Reading(snapshot.complete(), snapshot.missingSections()), now);
        return profiles.save(profile);
    }

    @Transactional(readOnly = true)
    public Optional<UserProfile> find(UUID userId) {
        return profiles.findById(userId);
    }

    /** Idempotente: {@code user.deleted} puede llegar más de una vez. */
    @Transactional
    public void delete(UUID userId) {
        tasks.deleteByUserId(userId);
        profiles.deleteById(userId);
    }
}

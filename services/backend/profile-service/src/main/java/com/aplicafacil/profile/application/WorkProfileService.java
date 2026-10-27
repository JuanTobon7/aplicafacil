package com.aplicafacil.profile.application;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aplicafacil.profile.application.exception.ProfileRejectedException;
import com.aplicafacil.profile.application.exception.ProfileRejectedException.Reason;
import com.aplicafacil.profile.application.port.out.UserProfileRepository;
import com.aplicafacil.profile.domain.Seniority;
import com.aplicafacil.profile.domain.UserProfile;
import com.aplicafacil.profile.domain.WorkProfile;
import com.aplicafacil.profile.domain.WorkRole;

/** La persona define su perfil laboral (Backend, Frontend, Fullstack…); la IA ya no lo cambia después. */
@Service
public class WorkProfileService {

    private final UserProfileRepository profiles;
    private final Clock clock;

    public WorkProfileService(UserProfileRepository profiles, Clock clock) {
        this.profiles = profiles;
        this.clock = clock;
    }

    @Transactional
    public WorkProfile define(UUID userId, Definition definition) {
        UserProfile profile = profiles.findById(userId).orElseGet(() -> UserProfile.create(userId, clock.instant()));
        try {
            profile.defineWorkProfile(new WorkProfile(definition.primaryRole(), definition.secondaryRoles(),
                    definition.seniority(), definition.title(), WorkProfile.Source.MANUAL, null), clock.instant());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new ProfileRejectedException(Reason.INVALID_REQUEST, e.getMessage());
        }
        return profiles.save(profile).workProfile();
    }

    /** Lo que la persona elige; la fecha y el origen los pone el dominio. */
    public record Definition(WorkRole primaryRole, List<WorkRole> secondaryRoles, Seniority seniority, String title) {
    }
}

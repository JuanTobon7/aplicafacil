package com.aplicafacil.profile.api;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.aplicafacil.linkedin.contract.ProfileSnapshot;
import com.aplicafacil.profile.application.ProfileSyncService;
import com.aplicafacil.profile.application.WorkProfileService;
import com.aplicafacil.profile.application.ai.SearchPreferenceService;
import com.aplicafacil.profile.domain.SearchPreference;
import com.aplicafacil.profile.domain.Seniority;
import com.aplicafacil.profile.domain.UserProfile;
import com.aplicafacil.profile.domain.WorkProfile;
import com.aplicafacil.profile.domain.WorkRole;

/**
 * Perfil laboral (contracts/openapi/profile-api.yaml). La persona solo ve el suyo ({@code sub} del JWT).
 */
@RestController
class ProfileController {

    private final ProfileSyncService profiles;
    private final SearchPreferenceService searches;
    private final WorkProfileService workProfiles;

    ProfileController(ProfileSyncService profiles, SearchPreferenceService searches, WorkProfileService workProfiles) {
        this.profiles = profiles;
        this.searches = searches;
        this.workProfiles = workProfiles;
    }

    @GetMapping("/profiles/me")
    @PreAuthorize("hasRole('USER')")
    ResponseEntity<ProfileResponse> me(@AuthenticationPrincipal Jwt jwt) {
        return profiles.find(UUID.fromString(jwt.getSubject()))
                .map(ProfileResponse::from)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/profiles/me/search-preference")
    @PreAuthorize("hasRole('USER')")
    SearchPreference configureSearch(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody SearchRequest request) {
        return searches.configure(UUID.fromString(jwt.getSubject()), new UserProfile.SearchSettings(
                request.keywords(), request.remoteOnly(), request.postedWithinDays(), request.location()));
    }

    /** Backend, Frontend, Fullstack… definido a mano: la IA ya no lo cambia. */
    @PutMapping("/profiles/me/work-profile")
    @PreAuthorize("hasRole('USER')")
    WorkProfile defineWorkProfile(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody WorkProfileRequest request) {
        return workProfiles.define(UUID.fromString(jwt.getSubject()), new WorkProfileService.Definition(
                request.primaryRole(), request.secondaryRoles(), request.seniority(), request.title()));
    }

    /** linkedin-service, al conectar o con "Sincronizar de nuevo". */
    @PutMapping("/internal/profiles/{userId}/sync")
    @PreAuthorize("hasRole('SERVICE')")
    ResponseEntity<Void> sync(@PathVariable UUID userId, @RequestBody ProfileSnapshot snapshot) {
        profiles.sync(userId, snapshot);
        return ResponseEntity.noContent().build();
    }

    record WorkProfileRequest(
            @NotNull WorkRole primaryRole,
            @Size(max = WorkProfile.MAX_SECONDARY) List<WorkRole> secondaryRoles,
            Seniority seniority,
            @Size(max = 120) String title) {
    }

    record SearchRequest(
            @NotBlank @Size(min = 2, max = 200) String keywords,
            boolean remoteOnly,
            @Min(1) @Max(30) Integer postedWithinDays,
            @Size(max = 120) String location) {
    }
}

package com.aplicafacil.profile.application.ai;

import java.time.Clock;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aplicafacil.profile.application.exception.ProfileRejectedException;
import com.aplicafacil.profile.application.exception.ProfileRejectedException.Reason;
import com.aplicafacil.profile.application.port.out.AiTaskRepository;
import com.aplicafacil.profile.application.port.out.UserProfileRepository;
import com.aplicafacil.profile.domain.AiTask;
import com.aplicafacil.profile.domain.AiTaskKind;
import com.aplicafacil.profile.domain.SearchPreference;
import com.aplicafacil.profile.domain.UserProfile;

/**
 * La búsqueda de empleo del perfil: la IA elige las palabras clave y la persona ajusta remoto, antigüedad de la
 * oferta y lugar.
 */
@Service
public class SearchPreferenceService {

    private final UserProfileRepository profiles;
    private final AiTaskRepository tasks;
    private final AiTaskSubmitter submitter;
    private final Clock clock;

    public SearchPreferenceService(UserProfileRepository profiles, AiTaskRepository tasks, AiTaskSubmitter submitter,
                                   Clock clock) {
        this.profiles = profiles;
        this.tasks = tasks;
        this.submitter = submitter;
        this.clock = clock;
    }

    /** Pide a la IA la búsqueda clave para el perfil actual. */
    @Transactional
    public AiTask requestSuggestion(UUID userId, UUID configId) {
        UserProfile profile = existing(userId);
        if (profile.content().isEmpty()) {
            throw new ProfileRejectedException(Reason.INVALID_REQUEST,
                    "El perfil está vacío: importa tu CV o sincroniza LinkedIn primero.");
        }
        if (tasks.hasPending(userId)) {
            throw new ProfileRejectedException(Reason.AI_TASK_IN_PROGRESS,
                    "Ya hay un CV o una búsqueda en proceso; espera a que termine.");
        }
        return submitSuggestion(profile, configId);
    }

    /** Después de importar un CV: misma transacción y mismo proveedor que la extracción. */
    AiTask submitSuggestion(UserProfile profile, UUID configId) {
        return submitter.submit(new AiTaskSubmitter.Request(profile.userId(), configId, AiTaskKind.SEARCH_SUGGESTION,
                null, ProfilePrompts.searchSuggestion(profile.content(), profile.workProfile())));
    }

    @Transactional
    public SearchPreference configure(UUID userId, UserProfile.SearchSettings settings) {
        UserProfile profile = profiles.findById(userId).orElseGet(() -> UserProfile.create(userId, clock.instant()));
        try {
            profile.configureSearch(settings, clock.instant());
        } catch (IllegalArgumentException e) {
            throw new ProfileRejectedException(Reason.INVALID_REQUEST, e.getMessage());
        }
        return profiles.save(profile).searchPreference();
    }

    private UserProfile existing(UUID userId) {
        return profiles.findById(userId).orElseThrow(() -> new ProfileRejectedException(Reason.PROFILE_NOT_FOUND,
                "Todavía no tienes perfil: importa tu CV o conecta LinkedIn."));
    }
}

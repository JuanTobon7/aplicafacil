package com.aplicafacil.profile.application.ai;

import java.time.Instant;

import org.springframework.stereotype.Component;

import com.aplicafacil.profile.application.port.out.AiOutputReader;
import com.aplicafacil.profile.application.port.out.AiOutputReader.InvalidAiOutputException;
import com.aplicafacil.profile.application.port.out.UserProfileRepository;
import com.aplicafacil.profile.domain.AiTask;
import com.aplicafacil.profile.domain.AiTaskKind;
import com.aplicafacil.profile.domain.ProfileContent;
import com.aplicafacil.profile.domain.UserProfile;

/** El CV descompuesto llena el perfil y, con el perfil ya armado, se pide la búsqueda sugerida. */
@Component
class CvExtractionCompletion implements AiTaskCompletion {

    private final AiOutputReader reader;
    private final UserProfileRepository profiles;
    private final SearchPreferenceService searches;

    CvExtractionCompletion(AiOutputReader reader, UserProfileRepository profiles, SearchPreferenceService searches) {
        this.reader = reader;
        this.profiles = profiles;
        this.searches = searches;
    }

    @Override
    public AiTaskKind kind() {
        return AiTaskKind.CV_EXTRACTION;
    }

    @Override
    public void apply(AiTask task, String output, Instant now) {
        ProfileContent extracted = reader.readProfile(output);
        if (extracted.isEmpty()) {
            throw new InvalidAiOutputException("La IA no encontró datos de perfil en el CV", null);
        }
        UserProfile profile = profiles.findById(task.userId()).orElseGet(() -> UserProfile.create(task.userId(), now));
        profile.importCv(extracted, now);
        UserProfile saved = profiles.save(profile);
        searches.submitSuggestion(saved, task.configId());
    }
}

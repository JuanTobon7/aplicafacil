package com.aplicafacil.profile.application.ai;

import java.time.Instant;

import org.springframework.stereotype.Component;

import com.aplicafacil.profile.application.port.out.AiOutputReader;
import com.aplicafacil.profile.application.port.out.UserProfileRepository;
import com.aplicafacil.profile.domain.AiTask;
import com.aplicafacil.profile.domain.AiTaskKind;
import com.aplicafacil.profile.domain.UserProfile;

/** La búsqueda elegida por la IA queda en el perfil; remoto, fecha y lugar siguen como los dejó la persona. */
@Component
class SearchSuggestionCompletion implements AiTaskCompletion {

    private final AiOutputReader reader;
    private final UserProfileRepository profiles;

    SearchSuggestionCompletion(AiOutputReader reader, UserProfileRepository profiles) {
        this.reader = reader;
        this.profiles = profiles;
    }

    @Override
    public AiTaskKind kind() {
        return AiTaskKind.SEARCH_SUGGESTION;
    }

    @Override
    public void apply(AiTask task, String output, Instant now) {
        UserProfile profile = profiles.findById(task.userId()).orElseGet(() -> UserProfile.create(task.userId(), now));
        profile.suggestSearch(reader.readSuggestion(output), now);
        profiles.save(profile);
    }
}

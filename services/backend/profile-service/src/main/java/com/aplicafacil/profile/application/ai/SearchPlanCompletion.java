package com.aplicafacil.profile.application.ai;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Component;

import com.aplicafacil.profile.application.port.out.SearchPlans;
import com.aplicafacil.profile.application.port.out.SearchPlans.SearchPlan;
import com.aplicafacil.profile.application.port.out.UserProfileRepository;
import com.aplicafacil.profile.domain.AiTask;
import com.aplicafacil.profile.domain.AiTaskKind;
import com.aplicafacil.profile.domain.SearchPreference;

/**
 * La IA eligió las búsquedas: quedan en el perfil (como una sugerencia) y se lanzan en los portales. La principal y
 * una alternativa: dos búsquedas por portal caben en el tope de búsquedas en curso de jobs-service.
 */
@Component
class SearchPlanCompletion implements AiTaskCompletion {

    static final int PLAN_SIZE = 2;

    private final SearchSuggestionCompletion suggestion;
    private final UserProfileRepository profiles;
    private final SearchPlans plans;

    SearchPlanCompletion(SearchSuggestionCompletion suggestion, UserProfileRepository profiles, SearchPlans plans) {
        this.suggestion = suggestion;
        this.profiles = profiles;
        this.plans = plans;
    }

    @Override
    public AiTaskKind kind() {
        return AiTaskKind.SEARCH_PLAN;
    }

    @Override
    public void apply(AiTask task, String output, Instant now) {
        suggestion.apply(task, output, now);
        SearchPreference p = profiles.findById(task.userId()).orElseThrow().searchPreference();
        List<String> searches = p.plannedQueries(PLAN_SIZE);
        if (searches.isEmpty()) {
            throw new IllegalArgumentException("La IA no eligió ninguna búsqueda");
        }
        plans.publish(new SearchPlan(task.id(), task.userId(), searches, p.remoteOnly(), p.postedWithinDays(),
                p.remoteOnly() ? null : p.location(), now));
    }
}

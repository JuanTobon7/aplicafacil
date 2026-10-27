package com.aplicafacil.profile.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class SearchPreferenceTest {

    @Test
    void plannedQueriesStartWithTheMainOneAndSkipRepeatsAndBlanks() {
        SearchPreference p = new SearchPreference("Desarrollador Java", List.of("desarrollador java", " ", "Backend Java",
                "Spring Boot"), null, true, null, null, null);

        assertThat(p.plannedQueries(2)).containsExactly("Desarrollador Java", "Backend Java");
        assertThat(p.plannedQueries(5)).containsExactly("Desarrollador Java", "Backend Java", "Spring Boot");
        assertThat(SearchPreference.NONE.plannedQueries(2)).isEmpty();
    }
}

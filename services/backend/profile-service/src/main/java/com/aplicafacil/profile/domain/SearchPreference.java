package com.aplicafacil.profile.domain;

import java.time.Instant;
import java.util.List;

/**
 * Cómo buscar empleo con este perfil. La IA elige las palabras clave ({@link #suggested}); la persona decide si solo
 * remoto, qué tan reciente debe ser la oferta y dónde ({@link #configured}). Una sugerencia nueva no cambia lo que la
 * persona configuró.
 *
 * @param keywords         {@code null} = todavía no hay búsqueda
 * @param postedWithinDays {@code null} = sin filtro de fecha; si no, 1..30 (lo que aceptan los portales)
 * @param suggestedAt      cuándo la eligió la IA ({@code null} si nunca)
 */
public record SearchPreference(String keywords, List<String> alternatives, String rationale, boolean remoteOnly,
                               Integer postedWithinDays, String location, Instant suggestedAt) {

    public static final SearchPreference NONE = new SearchPreference(null, List.of(), null, false, null, null, null);

    public SearchPreference {
        keywords = Text.clean(keywords, 200);
        alternatives = alternatives == null ? List.of() : List.copyOf(alternatives);
        rationale = Text.clean(rationale, 500);
        if (postedWithinDays != null && (postedWithinDays < 1 || postedWithinDays > 30)) {
            throw new IllegalArgumentException("postedWithinDays debe estar entre 1 y 30");
        }
        location = Text.clean(location, 120);
    }

    public SearchPreference suggested(SearchSuggestion suggestion, Instant now) {
        return new SearchPreference(suggestion.keywords(), suggestion.alternatives(), suggestion.rationale(),
                remoteOnly, postedWithinDays, location, now);
    }

    /** La persona ajusta la búsqueda; la sugerencia (alternativas, motivo) se conserva. */
    public SearchPreference configured(String newKeywords, boolean remote, Integer days, String where) {
        if (Text.clean(newKeywords, 200) == null || newKeywords.strip().length() < 2) {
            throw new IllegalArgumentException("keywords debe tener entre 2 y 200 caracteres");
        }
        return new SearchPreference(newKeywords, alternatives, rationale, remote, days, where, suggestedAt);
    }
}

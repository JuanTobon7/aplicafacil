package com.aplicafacil.profile.domain;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * La búsqueda de empleo que la IA eligió para el perfil ("desarrollador backend java spring boot").
 *
 * @param alternatives otras búsquedas razonables, sin repetir la principal (máximo 5)
 * @param rationale    por qué, en una o dos frases, para mostrarlo a la persona
 * @param workProfile  el tipo de perfil que la IA dedujo, o {@code null} si no lo dio o no era válido
 */
public record SearchSuggestion(String keywords, List<String> alternatives, String rationale,
                               WorkProfile workProfile) {

    public static final int MAX_ALTERNATIVES = 5;

    public SearchSuggestion {
        keywords = Text.required(keywords, 200, "keywords");
        String main = keywords.toLowerCase(Locale.ROOT);
        alternatives = alternatives == null ? List.of() : alternatives.stream()
                .map(a -> Text.clean(a, 200))
                .filter(Objects::nonNull)
                .filter(a -> a.length() >= 2 && !a.toLowerCase(Locale.ROOT).equals(main))
                .distinct()
                .limit(MAX_ALTERNATIVES)
                .toList();
        rationale = Text.clean(rationale, 500);
        if (keywords.length() < 2) {
            throw new IllegalArgumentException("keywords debe tener al menos 2 caracteres");
        }
    }
}

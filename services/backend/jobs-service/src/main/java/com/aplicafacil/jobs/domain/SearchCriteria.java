package com.aplicafacil.jobs.domain;

/**
 * Qué buscar, igual para todos los portales.
 *
 * @param location         {@code null} = cualquier lugar
 * @param postedWithinDays {@code null} = sin filtro de fecha
 * @param remote           solo ofertas remotas (el lugar deja de filtrar en los portales que no lo combinan)
 */
public record SearchCriteria(String keywords, String location, int maxResults, Integer postedWithinDays,
                             boolean remote) {

    public static final int MAX_RESULTS = 200;

    public SearchCriteria {
        if (keywords == null || keywords.isBlank() || keywords.strip().length() < 2 || keywords.strip().length() > 200) {
            throw new IllegalArgumentException("keywords debe tener entre 2 y 200 caracteres");
        }
        if (maxResults < 1 || maxResults > MAX_RESULTS) {
            throw new IllegalArgumentException("maxResults debe estar entre 1 y " + MAX_RESULTS);
        }
        if (postedWithinDays != null && (postedWithinDays < 1 || postedWithinDays > 30)) {
            throw new IllegalArgumentException("postedWithinDays debe estar entre 1 y 30");
        }
        keywords = keywords.strip();
        location = location == null || location.isBlank() ? null : location.strip();
    }

    /** Presencial o remota, sin filtrar por modalidad. */
    public SearchCriteria(String keywords, String location, int maxResults, Integer postedWithinDays) {
        this(keywords, location, maxResults, postedWithinDays, false);
    }
}

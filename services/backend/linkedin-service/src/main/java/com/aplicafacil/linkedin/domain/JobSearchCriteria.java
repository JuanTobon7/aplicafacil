package com.aplicafacil.linkedin.domain;

/**
 * Criterios de una búsqueda de empleo en LinkedIn (los mismos que entiende el scraper).
 *
 * @param postedWithinDays solo ofertas de los últimos N días; {@code null} = sin filtro
 * @param remote           solo ofertas remotas
 */
public record JobSearchCriteria(String keywords, String location, int maxResults, Integer postedWithinDays,
                                boolean remote) {

    /** Sin filtrar por modalidad. */
    public JobSearchCriteria(String keywords, String location, int maxResults, Integer postedWithinDays) {
        this(keywords, location, maxResults, postedWithinDays, false);
    }
}

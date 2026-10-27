package com.aplicafacil.linkedin.domain;

/** Una oferta leída de una card del listado de LinkedIn (contracts/openapi/scraper-browser.yaml, JobCard). */
public record JobListing(
        String externalId,
        String url,
        String title,
        String company,
        String location,
        String postedText,
        String salaryText,
        boolean quickApply) {
}

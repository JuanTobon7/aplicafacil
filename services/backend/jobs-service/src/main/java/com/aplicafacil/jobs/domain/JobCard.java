package com.aplicafacil.jobs.domain;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Una oferta leída de una card del listado, ya normalizada por el portal (contracts/openapi/scraper-browser.yaml).
 *
 * <p>Regla del título (la misma que aplica el scraper): espacios normalizados y nunca su propio texto repetido. Los
 * portales a veces lo duplican para lectores de pantalla ("Backend DeveloperBackend Developer").
 *
 * @param externalId id de la oferta en el portal: con él se deduplica
 */
public record JobCard(
        String externalId,
        String url,
        String title,
        String company,
        String location,
        String postedText,
        String salaryText,
        boolean quickApply) {

    /** El mismo texto dos veces, pegado o separado por un espacio. */
    private static final Pattern REPEATED = Pattern.compile("^(.+?) ?\\1$", Pattern.DOTALL);

    public JobCard {
        title = normalizeTitle(title);
    }

    /** Una card sin id, URL o título no se guarda. */
    public boolean isComplete() {
        return externalId != null && !externalId.isBlank() && url != null && url.startsWith("https://")
                && title != null && !title.isBlank();
    }

    static String normalizeTitle(String raw) {
        if (raw == null) {
            return null;
        }
        String text = raw.replaceAll("\\s+", " ").strip();
        Matcher repeated = REPEATED.matcher(text);
        return repeated.matches() ? repeated.group(1).strip() : text;
    }
}

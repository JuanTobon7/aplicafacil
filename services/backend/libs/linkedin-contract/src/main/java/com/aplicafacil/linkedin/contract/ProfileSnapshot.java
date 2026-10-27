package com.aplicafacil.linkedin.contract;

import java.time.YearMonth;
import java.util.List;

/**
 * Datos leídos del perfil para llenar postulaciones. {@code complete = false} indica una lectura
 * parcial: el usuario se crea igual con los datos mínimos y el perfil queda pendiente de reintento.
 */
public record ProfileSnapshot(
        String summary,
        List<Experience> experience,
        List<Education> education,
        List<String> skills,
        List<Language> languages,
        boolean complete,
        List<String> missingSections) {

    public ProfileSnapshot {
        experience = experience == null ? List.of() : List.copyOf(experience);
        education = education == null ? List.of() : List.copyOf(education);
        skills = skills == null ? List.of() : List.copyOf(skills);
        languages = languages == null ? List.of() : List.copyOf(languages);
        missingSections = missingSections == null ? List.of() : List.copyOf(missingSections);
    }

    /** @param endDate {@code null} = cargo actual */
    public record Experience(String title, String company, YearMonth startDate, YearMonth endDate,
                             String location, String description) {
    }

    public record Education(String school, String degree, String fieldOfStudy,
                            YearMonth startDate, YearMonth endDate, String description) {
    }

    public record Language(String name, String proficiency) {
    }
}

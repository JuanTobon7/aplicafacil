package com.aplicafacil.profile.domain;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * El contenido del perfil, venga de LinkedIn, de un CV o de lo guardado. Las skills y los idiomas repetidos (mismo
 * nombre sin importar mayúsculas) se unen en memoria con un mapa por clave: se queda la primera aparición.
 */
public record ProfileContent(String headline, String summary, List<Skill> skills, List<Experience> experiences,
                             List<Education> education, List<Language> languages,
                             List<Certification> certifications) {

    public static final ProfileContent EMPTY = new ProfileContent(null, null, null, null, null, null, null);

    public ProfileContent {
        headline = Text.clean(headline, 255);
        summary = Text.multiline(summary, 4000);
        skills = unique(skills, Skill::key);
        experiences = experiences == null ? List.of() : List.copyOf(experiences);
        education = education == null ? List.of() : List.copyOf(education);
        languages = unique(languages, Language::key);
        certifications = certifications == null ? List.of() : List.copyOf(certifications);
    }

    /** Las secciones que traen algo: lo vacío de un CV no borra lo que ya había. */
    public Set<ProfileSection> presentSections() {
        Set<ProfileSection> present = EnumSet.noneOf(ProfileSection.class);
        addIf(present, ProfileSection.HEADLINE, headline != null);
        addIf(present, ProfileSection.SUMMARY, summary != null);
        addIf(present, ProfileSection.SKILLS, !skills.isEmpty());
        addIf(present, ProfileSection.EXPERIENCE, !experiences.isEmpty());
        addIf(present, ProfileSection.EDUCATION, !education.isEmpty());
        addIf(present, ProfileSection.LANGUAGES, !languages.isEmpty());
        addIf(present, ProfileSection.CERTIFICATIONS, !certifications.isEmpty());
        return present;
    }

    public boolean isEmpty() {
        return presentSections().isEmpty();
    }

    private static void addIf(Set<ProfileSection> sections, ProfileSection section, boolean condition) {
        if (condition) {
            sections.add(section);
        }
    }

    private static <T> List<T> unique(List<T> items, Function<T, String> key) {
        if (items == null) {
            return List.of();
        }
        Map<String, T> byKey = new LinkedHashMap<>();
        for (T item : items) {
            byKey.putIfAbsent(key.apply(item), item);
        }
        return List.copyOf(byKey.values());
    }
}

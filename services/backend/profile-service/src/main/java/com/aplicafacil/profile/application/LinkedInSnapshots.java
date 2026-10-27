package com.aplicafacil.profile.application;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.aplicafacil.linkedin.contract.ProfileSnapshot;
import com.aplicafacil.profile.domain.Education;
import com.aplicafacil.profile.domain.Experience;
import com.aplicafacil.profile.domain.Language;
import com.aplicafacil.profile.domain.Period;
import com.aplicafacil.profile.domain.ProfileContent;
import com.aplicafacil.profile.domain.ProfileSection;
import com.aplicafacil.profile.domain.Skill;

/** Traduce una lectura de LinkedIn (contrato de linkedin-service) al contenido del perfil. */
final class LinkedInSnapshots {

    private static final String ALL_SECTIONS = "all";
    /** Nombre de cada sección en {@code missingSections} del contrato. LinkedIn no da título ni certificaciones. */
    private static final Map<ProfileSection, String> CONTRACT_NAMES = Map.of(
            ProfileSection.SUMMARY, "summary",
            ProfileSection.EXPERIENCE, "experience",
            ProfileSection.EDUCATION, "education",
            ProfileSection.SKILLS, "skills",
            ProfileSection.LANGUAGES, "languages");

    private LinkedInSnapshots() {
    }

    static ProfileContent content(ProfileSnapshot s) {
        return new ProfileContent(null, s.summary(),
                s.skills().stream().filter(LinkedInSnapshots::present).map(Skill::named).toList(),
                s.experience().stream().filter(e -> present(e.title()) && present(e.company()))
                        .map(LinkedInSnapshots::experience).toList(),
                s.education().stream().filter(e -> present(e.school())).map(LinkedInSnapshots::education).toList(),
                s.languages().stream().filter(l -> present(l.name()))
                        .map(l -> Language.of(l.name(), l.proficiency())).toList(),
                List.of());
    }

    /** Las secciones que LinkedIn sí leyó: las faltantes se buscan en un Set (una vez), no en la lista por sección. */
    static Set<ProfileSection> sectionsRead(ProfileSnapshot s) {
        Set<String> missing = new HashSet<>();
        s.missingSections().forEach(m -> missing.add(m.toLowerCase(Locale.ROOT)));
        Set<ProfileSection> read = EnumSet.noneOf(ProfileSection.class);
        if (missing.contains(ALL_SECTIONS)) {
            return read;
        }
        CONTRACT_NAMES.forEach((section, name) -> {
            if (!missing.contains(name)) {
                read.add(section);
            }
        });
        return read;
    }

    private static boolean present(String value) {
        return value != null && !value.isBlank();
    }

    private static Experience experience(ProfileSnapshot.Experience e) {
        return Experience.of(e.title(), e.company(), new Period(e.startDate(), e.endDate()), e.location(),
                e.description(), List.of());
    }

    private static Education education(ProfileSnapshot.Education e) {
        return Education.of(e.school(), e.degree(), e.fieldOfStudy(), new Period(e.startDate(), e.endDate()),
                e.description());
    }
}

package com.aplicafacil.profile.domain;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * Una habilidad del perfil (entidad: tiene id propio). Dos skills con el mismo nombre, sin importar mayúsculas, son la
 * misma ({@link #key()}).
 *
 * @param category          {@code null} = sin clasificar
 * @param level             {@code null} = no declarado
 * @param yearsOfExperience {@code null} = no declarado
 */
public record Skill(UUID id, String name, SkillCategory category, SkillLevel level, Integer yearsOfExperience) {

    public Skill {
        Objects.requireNonNull(id);
        name = Text.required(name, 100, "name");
        if (yearsOfExperience != null && (yearsOfExperience < 0 || yearsOfExperience > 60)) {
            yearsOfExperience = null;
        }
    }

    public static Skill of(String name, SkillCategory category, SkillLevel level, Integer yearsOfExperience) {
        return new Skill(UUID.randomUUID(), name, category, level, yearsOfExperience);
    }

    public static Skill named(String name) {
        return of(name, null, null, null);
    }

    /** Clave de deduplicación. */
    public String key() {
        return name.toLowerCase(Locale.ROOT);
    }
}

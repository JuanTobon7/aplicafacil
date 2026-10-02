package com.aplicafacil.profile.domain;

import static com.aplicafacil.profile.domain.DomainValidation.optionalText;
import static com.aplicafacil.profile.domain.DomainValidation.requireNonNull;
import static com.aplicafacil.profile.domain.DomainValidation.requireText;

import java.util.Objects;
import java.util.UUID;

/** Habilidad del perfil. Solo se modifica a través de {@link Profile}. */
public final class Skill {

    public static final int MAX_NAME_LENGTH = 100;
    public static final int MAX_DESCRIPTION_LENGTH = 255;

    private final UUID id;
    private final String name;
    private final String description;
    private final Integer yearsOfExperience;

    public Skill(UUID id, String name, String description, Integer yearsOfExperience) {
        this.id = requireNonNull(id, "id");
        this.name = requireText(name, "skill.name", MAX_NAME_LENGTH);
        this.description = optionalText(description, "skill.description", MAX_DESCRIPTION_LENGTH);
        if (yearsOfExperience != null && yearsOfExperience < 0) {
            throw new IllegalArgumentException("skill.yearsOfExperience no puede ser negativo");
        }
        this.yearsOfExperience = yearsOfExperience;
    }

    public static Skill create(String name, String description, Integer yearsOfExperience) {
        return new Skill(UUID.randomUUID(), name, description, yearsOfExperience);
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public Integer getYearsOfExperience() {
        return yearsOfExperience;
    }

    @Override
    public boolean equals(Object other) {
        return this == other || (other instanceof Skill skill && id.equals(skill.id));
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}

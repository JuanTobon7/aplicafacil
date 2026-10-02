package com.aplicafacil.profile.domain;

import static com.aplicafacil.profile.domain.DomainValidation.optionalText;
import static com.aplicafacil.profile.domain.DomainValidation.requireNonNull;
import static com.aplicafacil.profile.domain.DomainValidation.requireText;

import java.util.Objects;
import java.util.UUID;

/** Proyecto destacado del perfil. {@code technologies} es texto libre (p. ej. "Java, Spring, Postgres"). */
public final class Project {

    public static final int MAX_NAME_LENGTH = 255;
    public static final int MAX_TECHNOLOGIES_LENGTH = 255;

    private final UUID id;
    private final String name;
    private final String description;
    private final String technologies;

    public Project(UUID id, String name, String description, String technologies) {
        this.id = requireNonNull(id, "id");
        this.name = requireText(name, "project.name", MAX_NAME_LENGTH);
        this.description = requireText(description, "project.description", Integer.MAX_VALUE);
        this.technologies = optionalText(technologies, "project.technologies", MAX_TECHNOLOGIES_LENGTH);
    }

    public static Project create(String name, String description, String technologies) {
        return new Project(UUID.randomUUID(), name, description, technologies);
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

    public String getTechnologies() {
        return technologies;
    }

    @Override
    public boolean equals(Object other) {
        return this == other || (other instanceof Project project && id.equals(project.id));
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}

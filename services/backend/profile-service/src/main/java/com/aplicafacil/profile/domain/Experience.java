package com.aplicafacil.profile.domain;

import static com.aplicafacil.profile.domain.DomainValidation.optionalText;
import static com.aplicafacil.profile.domain.DomainValidation.requireNonNull;
import static com.aplicafacil.profile.domain.DomainValidation.requireText;
import static com.aplicafacil.profile.domain.DomainValidation.requireValidPeriod;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/** Experiencia laboral. {@code endDate == null} significa que sigue vigente. */
public final class Experience {

    public static final int MAX_COMPANY_NAME_LENGTH = 100;
    public static final int MAX_POSITION_LENGTH = 100;
    public static final int MAX_DESCRIPTION_LENGTH = 500;

    private final UUID id;
    private final String companyName;
    private final String position;
    private final String description;
    private final LocalDate startDate;
    private final LocalDate endDate;

    public Experience(UUID id, String companyName, String position, String description,
                      LocalDate startDate, LocalDate endDate) {
        this.id = requireNonNull(id, "id");
        this.companyName = requireText(companyName, "experience.companyName", MAX_COMPANY_NAME_LENGTH);
        this.position = requireText(position, "experience.position", MAX_POSITION_LENGTH);
        this.description = optionalText(description, "experience.description", MAX_DESCRIPTION_LENGTH);
        requireValidPeriod(startDate, endDate);
        this.startDate = startDate;
        this.endDate = endDate;
    }

    public static Experience create(String companyName, String position, String description,
                                    LocalDate startDate, LocalDate endDate) {
        return new Experience(UUID.randomUUID(), companyName, position, description, startDate, endDate);
    }

    public boolean isCurrent() {
        return endDate == null;
    }

    public UUID getId() {
        return id;
    }

    public String getCompanyName() {
        return companyName;
    }

    public String getPosition() {
        return position;
    }

    public String getDescription() {
        return description;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    @Override
    public boolean equals(Object other) {
        return this == other || (other instanceof Experience experience && id.equals(experience.id));
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}

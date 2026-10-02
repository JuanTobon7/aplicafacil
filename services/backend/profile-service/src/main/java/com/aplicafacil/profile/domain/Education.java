package com.aplicafacil.profile.domain;

import static com.aplicafacil.profile.domain.DomainValidation.optionalText;
import static com.aplicafacil.profile.domain.DomainValidation.requireNonNull;
import static com.aplicafacil.profile.domain.DomainValidation.requireText;
import static com.aplicafacil.profile.domain.DomainValidation.requireValidPeriod;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/** Formación académica. {@code endDate == null} significa que está en curso. */
public final class Education {

    public static final int MAX_INSTITUTION_NAME_LENGTH = 100;
    public static final int MAX_DESCRIPTION_LENGTH = 250;

    private final UUID id;
    private final String institutionName;
    private final String description;
    private final LocalDate startDate;
    private final LocalDate endDate;

    public Education(UUID id, String institutionName, String description,
                     LocalDate startDate, LocalDate endDate) {
        this.id = requireNonNull(id, "id");
        this.institutionName = requireText(institutionName, "education.institutionName", MAX_INSTITUTION_NAME_LENGTH);
        this.description = optionalText(description, "education.description", MAX_DESCRIPTION_LENGTH);
        requireValidPeriod(startDate, endDate);
        this.startDate = startDate;
        this.endDate = endDate;
    }

    public static Education create(String institutionName, String description,
                                   LocalDate startDate, LocalDate endDate) {
        return new Education(UUID.randomUUID(), institutionName, description, startDate, endDate);
    }

    public boolean isInProgress() {
        return endDate == null;
    }

    public UUID getId() {
        return id;
    }

    public String getInstitutionName() {
        return institutionName;
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
        return this == other || (other instanceof Education education && id.equals(education.id));
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}

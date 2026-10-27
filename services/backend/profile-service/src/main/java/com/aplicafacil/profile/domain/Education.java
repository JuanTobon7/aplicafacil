package com.aplicafacil.profile.domain;

import java.util.Objects;
import java.util.UUID;

/** Un estudio (entidad). */
public record Education(UUID id, String institution, String degree, String fieldOfStudy, Period period,
                        String description) {

    public Education {
        Objects.requireNonNull(id);
        institution = Text.required(institution, 200, "institution");
        degree = Text.clean(degree, 200);
        fieldOfStudy = Text.clean(fieldOfStudy, 200);
        period = period == null ? new Period(null, null) : period;
        description = Text.multiline(description, 2000);
    }

    public static Education of(String institution, String degree, String fieldOfStudy, Period period,
                               String description) {
        return new Education(UUID.randomUUID(), institution, degree, fieldOfStudy, period, description);
    }
}

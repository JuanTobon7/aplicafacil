package com.aplicafacil.profile.domain;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Un cargo (entidad).
 *
 * @param period       {@code period.end() = null}: cargo actual
 * @param technologies lo que usó en ese cargo, tal como lo nombra el CV
 */
public record Experience(UUID id, String title, String company, String location, Period period, String description,
                         List<String> technologies) {

    public Experience {
        Objects.requireNonNull(id);
        title = Text.required(title, 200, "title");
        company = Text.required(company, 200, "company");
        location = Text.clean(location, 200);
        period = period == null ? new Period(null, null) : period;
        description = Text.multiline(description, 4000);
        technologies = technologies == null ? List.of() : technologies.stream()
                .map(t -> Text.clean(t, 100)).filter(Objects::nonNull).distinct().limit(40).toList();
    }

    public static Experience of(String title, String company, Period period, String location, String description,
                                List<String> technologies) {
        return new Experience(UUID.randomUUID(), title, company, location, period, description, technologies);
    }
}

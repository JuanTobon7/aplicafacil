package com.aplicafacil.profile.domain;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Qué tipo de profesional es la persona: un rol principal, hasta {@value #MAX_SECONDARY} secundarios (alguien Backend
 * que también hace Frontend), el seniority y cómo se presenta. Lo infiere la IA o lo define la persona.
 *
 * @param seniority {@code null} = no se sabe
 * @param title     obligatorio con {@link WorkRole#OTHER} (perfiles fuera del catálogo)
 */
public record WorkProfile(WorkRole primaryRole, List<WorkRole> secondaryRoles, Seniority seniority, String title,
                          Source source, Instant updatedAt) {

    public static final int MAX_SECONDARY = 3;

    /** Quién lo definió: la IA no pisa uno definido a mano. */
    public enum Source { AI, MANUAL }

    public WorkProfile {
        Objects.requireNonNull(primaryRole, "primaryRole");
        Objects.requireNonNull(source, "source");
        title = Text.clean(title, 120);
        if (primaryRole == WorkRole.OTHER && title == null) {
            throw new IllegalArgumentException("Con el rol OTHER el título es obligatorio");
        }
        secondaryRoles = secondary(primaryRole, secondaryRoles);
    }

    /** Sin repetidos ni el principal, en el orden dado, como mucho {@value #MAX_SECONDARY}. */
    private static List<WorkRole> secondary(WorkRole primary, List<WorkRole> roles) {
        if (roles == null) {
            return List.of();
        }
        Set<WorkRole> distinct = new LinkedHashSet<>(roles);
        distinct.remove(null);
        distinct.remove(primary);
        if (distinct.size() > MAX_SECONDARY) {
            throw new IllegalArgumentException("Como mucho " + MAX_SECONDARY + " roles secundarios");
        }
        return List.copyOf(distinct);
    }
}

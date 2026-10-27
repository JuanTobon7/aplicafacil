package com.aplicafacil.profile.domain;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/** Un idioma (entidad). {@code proficiency} es libre ("Nativo", "B2", "Profesional"…). */
public record Language(UUID id, String name, String proficiency) {

    public Language {
        Objects.requireNonNull(id);
        name = Text.required(name, 100, "name");
        proficiency = Text.clean(proficiency, 100);
    }

    public static Language of(String name, String proficiency) {
        return new Language(UUID.randomUUID(), name, proficiency);
    }

    /** Clave de deduplicación. */
    public String key() {
        return name.toLowerCase(Locale.ROOT);
    }
}

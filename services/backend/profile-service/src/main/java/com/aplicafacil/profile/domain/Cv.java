package com.aplicafacil.profile.domain;

import static com.aplicafacil.profile.domain.DomainValidation.requireNonNull;
import static com.aplicafacil.profile.domain.DomainValidation.requireText;

import java.util.Objects;
import java.util.UUID;

/**
 * Hoja de vida adjunta al perfil.
 *
 * <p>{@code location} es una referencia opaca al archivo (ruta local, key de S3
 * o URL): el dominio no sabe dónde ni cómo se almacena. Interpretarla es
 * responsabilidad del adaptador de almacenamiento.
 */
public final class Cv {

    public static final int MAX_LOCATION_LENGTH = 1024;
    public static final int MAX_MIME_TYPE_LENGTH = 255;

    private final UUID id;
    private final String location;
    private final String mimeType;

    public Cv(UUID id, String location, String mimeType) {
        this.id = requireNonNull(id, "id");
        this.location = requireText(location, "cv.location", MAX_LOCATION_LENGTH);
        this.mimeType = requireText(mimeType, "cv.mimeType", MAX_MIME_TYPE_LENGTH);
    }

    public static Cv create(String location, String mimeType) {
        return new Cv(UUID.randomUUID(), location, mimeType);
    }

    public UUID getId() {
        return id;
    }

    public String getLocation() {
        return location;
    }

    public String getMimeType() {
        return mimeType;
    }

    @Override
    public boolean equals(Object other) {
        return this == other || (other instanceof Cv cv && id.equals(cv.id));
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}

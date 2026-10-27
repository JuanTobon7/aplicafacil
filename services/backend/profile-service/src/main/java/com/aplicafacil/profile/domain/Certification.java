package com.aplicafacil.profile.domain;

import java.time.YearMonth;
import java.util.Objects;
import java.util.UUID;

/** Una certificación o curso (entidad). */
public record Certification(UUID id, String name, String issuer, YearMonth issuedOn) {

    public Certification {
        Objects.requireNonNull(id);
        name = Text.required(name, 255, "name");
        issuer = Text.clean(issuer, 200);
    }

    public static Certification of(String name, String issuer, YearMonth issuedOn) {
        return new Certification(UUID.randomUUID(), name, issuer, issuedOn);
    }
}

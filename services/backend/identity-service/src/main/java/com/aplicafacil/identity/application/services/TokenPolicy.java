package com.aplicafacil.identity.application.services;

import java.time.Duration;
import java.util.Objects;

/** Vigencias que usan los casos de uso; las arma infrastructure a partir de sus propiedades. */
public record TokenPolicy(Duration refreshTokenTtl, Duration loginCodeTtl) {

    public TokenPolicy {
        Objects.requireNonNull(refreshTokenTtl);
        Objects.requireNonNull(loginCodeTtl);
    }
}

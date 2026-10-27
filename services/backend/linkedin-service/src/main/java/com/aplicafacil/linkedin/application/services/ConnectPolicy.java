package com.aplicafacil.linkedin.application.services;

import java.time.Duration;
import java.util.Objects;

/** Lo que la conexión necesita de la configuración; la arma infrastructure a partir de sus propiedades. */
public record ConnectPolicy(Duration loginTimeout) {

    public ConnectPolicy {
        Objects.requireNonNull(loginTimeout);
    }
}

package com.aplicafacil.gateway.local;

import java.nio.file.Path;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Protección de la API local (ADR 0003, sección 8).
 *
 * @param enabled        exigir el token local en {@code /api/**} (fase local)
 * @param allowedHosts   valores aceptados del header Host (contra DNS rebinding)
 * @param allowedOrigins orígenes aceptados cuando el navegador envía Origin
 * @param frontDist      carpeta del build de React que sirve el gateway; vacía = no sirve el front
 */
@ConfigurationProperties("aplicafacil.local-access")
public record LocalAccessProperties(boolean enabled, List<String> allowedHosts, List<String> allowedOrigins,
                                    Path frontDist) {

    public LocalAccessProperties {
        allowedHosts = allowedHosts == null ? List.of() : List.copyOf(allowedHosts);
        allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
    }
}

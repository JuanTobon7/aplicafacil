package com.aplicafacil.aiconfig.application.port.out;

import java.time.Instant;
import java.util.List;

import com.aplicafacil.aiconfig.domain.ProviderConfig;

/** Pruebas de conexión que siguen en curso, para cerrarlas si el mcp-gateway nunca respondió. */
public interface PendingTests {

    /** Pruebas en curso que empezaron antes de {@code limit}. */
    List<ProviderConfig> startedBefore(Instant limit);
}

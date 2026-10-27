package com.aplicafacil.identity.application.port.out;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/** Lo que identity-service necesita de linkedin-service. Nunca recibe cookies ni el storageState. */
public interface LinkedInSessionClient {

    enum SessionCheck { VALID, EXPIRED, NOT_CONNECTED, BUSY }

    /** Valida la sesión guardada (navegador headless) y renueva su storageState. */
    SessionCheck validate(UUID userId);

    /** Borra la sesión. Se llama de forma síncrona antes de borrar la cuenta. */
    void deleteSession(UUID userId);

    /** Estado de conexión (DISCONNECTED, CONNECTED, EXPIRED…) para el selector de cuentas. */
    Map<UUID, String> connectionStatuses(Collection<UUID> userIds);
}

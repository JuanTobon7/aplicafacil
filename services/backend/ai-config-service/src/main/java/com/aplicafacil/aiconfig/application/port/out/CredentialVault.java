package com.aplicafacil.aiconfig.application.port.out;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Guarda las API keys cifradas. Es el único lugar que cifra y descifra; fuera de aquí solo circula una pista
 * ({@code hint}, los últimos caracteres).
 */
public interface CredentialVault {

    StoredCredential store(UUID userId, String apiKey, Instant now);

    Optional<String> hint(UUID credentialId);

    Map<UUID, String> hints(Collection<UUID> credentialIds);

    /**
     * Descifra la key. Si la llave de cifrado cambió lanza {@code AiConfigException} con
     * {@code CREDENTIAL_UNREADABLE}; si no existe, {@code configNotFound}.
     */
    String reveal(UUID credentialId);

    void delete(UUID credentialId);

    void deleteByUser(UUID userId);

    record StoredCredential(UUID id, String hint) {
    }
}

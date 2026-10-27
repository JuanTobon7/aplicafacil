package com.aplicafacil.linkedin.application.port.out;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.aplicafacil.platform.crypto.SessionDecryptionException;

/**
 * Único punto de lectura y escritura de la sesión de LinkedIn. Cifra y descifra internamente con
 * {@code SessionCryptoService}: fuera de aquí el storageState solo existe como String en memoria.
 */
public interface LinkedInSessionRepository {

    /**
     * @return el storageState descifrado, o vacío si el usuario no tiene sesión
     * @throws SessionDecryptionException si la llave cambió; quien llama marca la conexión como EXPIRED
     */
    Optional<String> loadStorageState(UUID userId);

    /** Crea o reemplaza la sesión. Cifra con un IV nuevo en cada escritura. */
    void saveStorageState(UUID userId, String storageStateJson);

    boolean exists(UUID userId);

    void markValidated(UUID userId, Instant validatedAt);

    /** Cuándo se comprobó por última vez que la sesión sigue viva (iniciarla también cuenta). */
    Optional<Instant> lastValidatedAt(UUID userId);

    /** Desconectar: borra la fila. No falla si no existe. */
    void delete(UUID userId);

    /**
     * Bloqueo por usuario: solo una automatización a la vez sobre la misma sesión.
     *
     * @return el arrendamiento, o vacío si otra ejecución lo tiene vigente
     */
    Optional<SessionLease> tryAcquireLease(UUID userId, Duration duration);

    void releaseLease(SessionLease lease);

    record SessionLease(UUID userId, UUID owner, Instant expiresAt) {
    }
}

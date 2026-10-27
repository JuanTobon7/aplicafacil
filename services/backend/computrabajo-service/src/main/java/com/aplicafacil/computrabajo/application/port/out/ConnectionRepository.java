package com.aplicafacil.computrabajo.application.port.out;

import java.util.Optional;
import java.util.UUID;

import com.aplicafacil.computrabajo.domain.ComputrabajoConnection;

/**
 * Único punto de lectura y escritura de la conexión y su sesión. Cifra y descifra internamente: fuera de aquí el
 * storageState solo existe como String en memoria.
 */
public interface ConnectionRepository {

    Optional<ComputrabajoConnection> find(UUID userId);

    /** Crea o reemplaza la conexión con su sesión. Cifra con un IV nuevo en cada escritura. */
    void save(ComputrabajoConnection connection, String storageState);

    /** Cambia solo el estado (la sesión guardada se conserva). */
    void update(ComputrabajoConnection connection);

    /**
     * @return el storageState descifrado, o vacío si no hay conexión
     * @throws com.aplicafacil.computrabajo.application.exception.SessionUnreadableException si la llave cambió
     */
    Optional<String> loadStorageState(UUID userId);

    /** Desconectar: borra la fila. No falla si no existe. */
    void delete(UUID userId);
}

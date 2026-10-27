package com.aplicafacil.aiconfig.application.port.out;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.aplicafacil.aiconfig.domain.ProviderConfig;

/** Configuraciones de proveedores ({@code provider_config}). Las consultas de un solo cliente van en su propio puerto. */
public interface ProviderConfigRepository {

    /** Las de una persona, de la más antigua a la más nueva. */
    List<ProviderConfig> findByUser(UUID userId);

    List<ProviderConfig> findAll();

    /** Una configuración de otra persona se trata igual que una inexistente. */
    Optional<ProviderConfig> findOwned(UUID configId, UUID userId);

    /** Bloquea la fila hasta el fin de la transacción: cambios y estados del mcp-gateway se serializan. */
    Optional<ProviderConfig> findForUpdate(UUID configId);

    /** Escribe de inmediato (la credencial anterior se borra después y la referencia debe estar actualizada). */
    void save(ProviderConfig config);

    void delete(UUID configId);

    void deleteByUser(UUID userId);
}

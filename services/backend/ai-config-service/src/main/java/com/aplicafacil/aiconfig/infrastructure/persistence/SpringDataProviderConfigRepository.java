package com.aplicafacil.aiconfig.infrastructure.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import com.aplicafacil.aiconfig.infrastructure.persistence.entity.ProviderConfigEntity;

public interface SpringDataProviderConfigRepository extends JpaRepository<ProviderConfigEntity, UUID> {

    List<ProviderConfigEntity> findByUserIdOrderByCreatedAtAsc(UUID userId);

    Optional<ProviderConfigEntity> findByIdAndUserId(UUID id, UUID userId);

    /** Cambios de la persona y estados del mcp-gateway sobre la misma fila se serializan. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from ProviderConfigEntity c where c.id = :id")
    Optional<ProviderConfigEntity> findForUpdate(UUID id);

    long countByUserId(UUID userId);

    boolean existsByUserIdAndDisplayNameIgnoreCase(UUID userId, String displayName);

    List<ProviderConfigEntity> findByUserId(UUID userId);

    /** Pruebas de conexión en curso que ya vencieron. */
    List<ProviderConfigEntity> findByLastTestSuccessIsNullAndLastTestAtBefore(Instant limit);
}

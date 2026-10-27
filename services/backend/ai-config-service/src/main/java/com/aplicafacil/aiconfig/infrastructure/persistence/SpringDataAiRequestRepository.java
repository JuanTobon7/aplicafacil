package com.aplicafacil.aiconfig.infrastructure.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.aplicafacil.aiconfig.domain.AiRequestStatus;
import com.aplicafacil.aiconfig.infrastructure.persistence.entity.AiRequestEntity;

public interface SpringDataAiRequestRepository extends JpaRepository<AiRequestEntity, UUID> {

    Optional<AiRequestEntity> findByIdAndUserId(UUID id, UUID userId);

    /** Dos copias del mismo resultado (entrega al menos una vez) no se aplican a la vez. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from AiRequestEntity r where r.id = :id")
    Optional<AiRequestEntity> findForUpdate(UUID id);

    List<AiRequestEntity> findByUserIdOrderByCreatedAtDesc(UUID userId, Limit limit);

    /** Cierra las solicitudes sin respuesta; un resultado que llegue después se ignora. */
    @Modifying
    @Query("""
            update AiRequestEntity r
               set r.status = com.aplicafacil.aiconfig.domain.AiRequestStatus.TIMED_OUT,
                   r.errorCode = 'TIMEOUT',
                   r.errorMessage = 'El proveedor no respondió a tiempo.',
                   r.completedAt = :now
             where r.status = :pending and r.deadline < :now
            """)
    int expire(Instant now, AiRequestStatus pending);

    void deleteByUserId(UUID userId);
}

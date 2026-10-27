package com.aplicafacil.profile.infrastructure.persistence;

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

import com.aplicafacil.profile.domain.AiTaskStatus;
import com.aplicafacil.profile.infrastructure.persistence.entity.AiTaskEntity;

interface SpringDataAiTaskRepository extends JpaRepository<AiTaskEntity, UUID> {

    Optional<AiTaskEntity> findByIdAndUserId(UUID id, UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from AiTaskEntity t where t.id = :id")
    Optional<AiTaskEntity> findForUpdate(UUID id);

    List<AiTaskEntity> findByUserIdOrderByCreatedAtDesc(UUID userId, Limit limit);

    /** Una pendiente que ya venció no bloquea pedir otra (el expirador la cerrará). */
    boolean existsByUserIdAndStatusAndDeadlineAfter(UUID userId, AiTaskStatus status, Instant now);

    @Modifying
    @Query("""
            update AiTaskEntity t
               set t.status = com.aplicafacil.profile.domain.AiTaskStatus.TIMED_OUT,
                   t.errorCode = 'TIMEOUT',
                   t.errorMessage = 'La IA no respondió a tiempo. Revisa que el proveedor esté activo.',
                   t.completedAt = :now
             where t.status = com.aplicafacil.profile.domain.AiTaskStatus.PENDING and t.deadline < :now
            """)
    int expire(Instant now);

    @Modifying
    @Query("delete from AiTaskEntity t where t.userId = :userId")
    int deleteByUserId(UUID userId);
}

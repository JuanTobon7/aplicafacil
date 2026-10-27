package com.aplicafacil.profile.infrastructure.persistence;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Repository;

import com.aplicafacil.profile.application.port.out.AiTaskRepository;
import com.aplicafacil.profile.domain.AiTask;
import com.aplicafacil.profile.domain.AiTaskStatus;
import com.aplicafacil.profile.infrastructure.persistence.entity.AiTaskEntity;

/** Adaptador JPA de {@link AiTaskRepository}. */
@Repository
public class JpaAiTaskRepository implements AiTaskRepository {

    private final SpringDataAiTaskRepository jpa;
    private final Clock clock;

    public JpaAiTaskRepository(SpringDataAiTaskRepository jpa, Clock clock) {
        this.jpa = jpa;
        this.clock = clock;
    }

    @Override
    public AiTask save(AiTask task) {
        AiTask.Window window = task.window();
        AiTask.Outcome outcome = task.outcome();
        AiTaskEntity entity = jpa.findById(task.id()).orElseGet(() -> AiTaskEntity.builder()
                .id(task.id()).userId(task.userId()).configId(task.configId()).kind(task.kind())
                .fileName(task.fileName()).createdAt(window.createdAt()).deadline(window.deadline())
                .build());
        entity.setStatus(outcome.status());
        entity.setErrorCode(outcome.errorCode());
        entity.setErrorMessage(outcome.errorMessage());
        entity.setCompletedAt(outcome.completedAt());
        return toDomain(jpa.save(entity));
    }

    @Override
    public Optional<AiTask> findOwned(UUID taskId, UUID userId) {
        return jpa.findByIdAndUserId(taskId, userId).map(JpaAiTaskRepository::toDomain);
    }

    @Override
    public Optional<AiTask> findForUpdate(UUID taskId) {
        return jpa.findForUpdate(taskId).map(JpaAiTaskRepository::toDomain);
    }

    @Override
    public List<AiTask> recent(UUID userId, int limit) {
        return jpa.findByUserIdOrderByCreatedAtDesc(userId, Limit.of(limit)).stream()
                .map(JpaAiTaskRepository::toDomain).toList();
    }

    @Override
    public boolean hasPending(UUID userId) {
        return jpa.existsByUserIdAndStatusAndDeadlineAfter(userId, AiTaskStatus.PENDING, clock.instant());
    }

    @Override
    public int expire(Instant now) {
        return jpa.expire(now);
    }

    @Override
    public void deleteByUserId(UUID userId) {
        jpa.deleteByUserId(userId);
    }

    private static AiTask toDomain(AiTaskEntity e) {
        return AiTask.restore(new AiTask.Origin(e.getId(), e.getUserId(), e.getConfigId()), e.getKind(),
                e.getFileName(), new AiTask.Window(e.getCreatedAt(), e.getDeadline()),
                new AiTask.Outcome(e.getStatus(), e.getErrorCode(), e.getErrorMessage(), e.getCompletedAt()));
    }
}

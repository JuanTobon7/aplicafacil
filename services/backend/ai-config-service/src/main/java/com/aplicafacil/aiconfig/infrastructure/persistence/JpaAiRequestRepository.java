package com.aplicafacil.aiconfig.infrastructure.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Repository;

import com.aplicafacil.aiconfig.application.port.out.AiRequestRepository;
import com.aplicafacil.aiconfig.domain.AiRequest;
import com.aplicafacil.aiconfig.domain.AiRequestStatus;
import com.aplicafacil.aiconfig.infrastructure.persistence.entity.AiRequestEntity;

/** Adaptador JPA de {@link AiRequestRepository}. */
@Repository
public class JpaAiRequestRepository implements AiRequestRepository {

    private final SpringDataAiRequestRepository jpa;

    public JpaAiRequestRepository(SpringDataAiRequestRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public void save(AiRequest request) {
        AiRequest.Snapshot s = request.snapshot();
        AiRequestEntity e = jpa.findById(s.id()).orElseGet(AiRequestEntity::new);
        e.setId(s.id());
        e.setUserId(s.userId());
        e.setConfigId(s.configId());
        e.setCorrelationId(s.correlationId());
        e.setStatus(s.status());
        e.setLatencyMs(s.latencyMs());
        e.setCreatedAt(s.createdAt());
        e.setDeadline(s.deadline());
        e.setCompletedAt(s.completedAt());
        copyOutput(s.output(), e);
        copyFailure(s.failure(), e);
        jpa.save(e);
    }

    @Override
    public Optional<AiRequest> findOwned(UUID requestId, UUID userId) {
        return jpa.findByIdAndUserId(requestId, userId).map(JpaAiRequestRepository::toDomain);
    }

    @Override
    public List<AiRequest> recent(UUID userId, int limit) {
        return jpa.findByUserIdOrderByCreatedAtDesc(userId, Limit.of(limit)).stream()
                .map(JpaAiRequestRepository::toDomain).toList();
    }

    @Override
    public Optional<AiRequest> findForUpdate(UUID requestId) {
        return jpa.findForUpdate(requestId).map(JpaAiRequestRepository::toDomain);
    }

    @Override
    public int expire(Instant now) {
        return jpa.expire(now, AiRequestStatus.PENDING);
    }

    private static void copyOutput(AiRequest.Output output, AiRequestEntity e) {
        if (output != null) {
            e.setOutputText(output.text());
            e.setOutputModel(output.model());
            e.setFinishReason(output.finishReason());
            e.setInputTokens(output.inputTokens());
            e.setOutputTokens(output.outputTokens());
        }
    }

    private static void copyFailure(AiRequest.Failure failure, AiRequestEntity e) {
        if (failure != null) {
            e.setErrorCode(failure.code());
            e.setErrorMessage(failure.message());
        }
    }

    private static AiRequest toDomain(AiRequestEntity e) {
        AiRequest.Output output = e.getStatus() == AiRequestStatus.COMPLETED ? new AiRequest.Output(e.getOutputText(),
                e.getOutputModel(), e.getFinishReason(), e.getInputTokens(), e.getOutputTokens()) : null;
        AiRequest.Failure failure = e.getErrorCode() == null && e.getErrorMessage() == null ? null
                : new AiRequest.Failure(e.getErrorCode(), e.getErrorMessage());
        return AiRequest.restore(new AiRequest.Snapshot(e.getId(), e.getUserId(), e.getConfigId(),
                e.getCorrelationId(), e.getStatus(), output, failure, e.getLatencyMs(), e.getCreatedAt(),
                e.getDeadline(), e.getCompletedAt()));
    }
}

package com.aplicafacil.linkedin.infrastructure.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Repository;

import com.aplicafacil.linkedin.application.port.out.LinkedInConnectionRepository;
import com.aplicafacil.linkedin.domain.LinkedInConnection;
import com.aplicafacil.linkedin.infrastructure.persistence.entity.LinkedInConnectionEntity;

/** Adaptador JPA de {@link LinkedInConnectionRepository}: traduce entre la entidad y el dominio. */
@Repository
public class JpaLinkedInConnectionRepository implements LinkedInConnectionRepository {

    private final SpringDataLinkedInConnectionRepository jpa;

    public JpaLinkedInConnectionRepository(SpringDataLinkedInConnectionRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Optional<LinkedInConnection> findById(UUID userId) {
        return jpa.findById(userId).map(JpaLinkedInConnectionRepository::toDomain);
    }

    @Override
    public List<LinkedInConnection> findAllById(Collection<UUID> userIds) {
        return jpa.findAllById(userIds).stream().map(JpaLinkedInConnectionRepository::toDomain).toList();
    }

    @Override
    public LinkedInConnection save(LinkedInConnection connection) {
        LinkedInConnectionEntity entity = jpa.findById(connection.userId()).orElseGet(() ->
                LinkedInConnectionEntity.builder().userId(connection.userId()).createdAt(connection.createdAt()).build());
        LinkedInConnection.State state = connection.state();
        LinkedInConnection.Timeline timeline = connection.timeline();
        entity.setStatus(state.status());
        entity.setConnectionType(state.connectionType());
        entity.setMemberUrn(state.memberUrn());
        entity.setLastError(state.lastError());
        entity.setConnectedAt(timeline.connectedAt());
        entity.setLastSyncAt(timeline.lastSyncAt());
        entity.setExpiredAt(timeline.expiredAt());
        entity.setUpdatedAt(timeline.updatedAt());
        return toDomain(jpa.save(entity));
    }

    @Override
    public void releaseMember(String memberUrn, UUID newOwner) {
        jpa.findByMemberUrn(memberUrn)
                .filter(other -> !other.getUserId().equals(newOwner))
                .ifPresent(other -> other.setMemberUrn(null));
        jpa.flush();
    }

    @Override
    public void deleteById(UUID userId) {
        jpa.deleteById(userId);
    }

    private static LinkedInConnection toDomain(LinkedInConnectionEntity e) {
        return LinkedInConnection.restore(e.getUserId(), e.getCreatedAt(),
                new LinkedInConnection.State(e.getStatus(), e.getConnectionType(), e.getMemberUrn(), e.getLastError()),
                new LinkedInConnection.Timeline(e.getConnectedAt(), e.getLastSyncAt(), e.getExpiredAt(),
                        e.getUpdatedAt()));
    }
}

package com.aplicafacil.profile.infrastructure.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.aplicafacil.profile.infrastructure.persistence.entity.OutboxEventEntity;

public interface SpringDataOutboxEventRepository extends JpaRepository<OutboxEventEntity, UUID> {

    List<OutboxEventEntity> findTop50ByPublishedAtIsNullOrderByCreatedAtAsc();
}

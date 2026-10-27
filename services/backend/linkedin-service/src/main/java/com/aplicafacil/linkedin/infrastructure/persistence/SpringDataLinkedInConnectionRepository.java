package com.aplicafacil.linkedin.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.aplicafacil.linkedin.infrastructure.persistence.entity.LinkedInConnectionEntity;

public interface SpringDataLinkedInConnectionRepository extends JpaRepository<LinkedInConnectionEntity, UUID> {

    Optional<LinkedInConnectionEntity> findByMemberUrn(String memberUrn);
}

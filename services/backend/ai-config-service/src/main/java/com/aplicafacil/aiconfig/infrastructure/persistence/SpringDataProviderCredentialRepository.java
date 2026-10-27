package com.aplicafacil.aiconfig.infrastructure.persistence;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.aplicafacil.aiconfig.infrastructure.persistence.entity.ProviderCredentialEntity;

public interface SpringDataProviderCredentialRepository extends JpaRepository<ProviderCredentialEntity, UUID> {

    void deleteByUserId(UUID userId);
}

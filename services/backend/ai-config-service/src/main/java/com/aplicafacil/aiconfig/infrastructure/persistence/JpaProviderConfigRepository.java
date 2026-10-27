package com.aplicafacil.aiconfig.infrastructure.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Repository;

import com.aplicafacil.aiconfig.application.port.out.ConfigQuota;
import com.aplicafacil.aiconfig.application.port.out.PendingTests;
import com.aplicafacil.aiconfig.application.port.out.ProviderConfigRepository;
import com.aplicafacil.aiconfig.domain.ProviderConfig;
import com.aplicafacil.aiconfig.infrastructure.persistence.entity.ProviderConfigEntity;

/** Adaptador JPA de los puertos de {@code provider_config}; la traducción está en {@link ProviderConfigMapper}. */
@Repository
public class JpaProviderConfigRepository implements ProviderConfigRepository, ConfigQuota, PendingTests {

    private final SpringDataProviderConfigRepository jpa;

    public JpaProviderConfigRepository(SpringDataProviderConfigRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public List<ProviderConfig> findByUser(UUID userId) {
        return toDomain(jpa.findByUserIdOrderByCreatedAtAsc(userId));
    }

    @Override
    public List<ProviderConfig> findAll() {
        return toDomain(jpa.findAll());
    }

    @Override
    public Optional<ProviderConfig> findOwned(UUID configId, UUID userId) {
        return jpa.findByIdAndUserId(configId, userId).map(ProviderConfigMapper::toDomain);
    }

    @Override
    public Optional<ProviderConfig> findForUpdate(UUID configId) {
        return jpa.findForUpdate(configId).map(ProviderConfigMapper::toDomain);
    }

    @Override
    public long countByUser(UUID userId) {
        return jpa.countByUserId(userId);
    }

    @Override
    public boolean nameTaken(UUID userId, String displayName) {
        return jpa.existsByUserIdAndDisplayNameIgnoreCase(userId, displayName);
    }

    @Override
    public List<ProviderConfig> startedBefore(Instant limit) {
        return toDomain(jpa.findByLastTestSuccessIsNullAndLastTestAtBefore(limit));
    }

    @Override
    public void save(ProviderConfig config) {
        ProviderConfigEntity entity = jpa.findById(config.id()).orElseGet(ProviderConfigEntity::new);
        ProviderConfigMapper.copy(config.snapshot(), entity);
        jpa.saveAndFlush(entity);
    }

    @Override
    public void delete(UUID configId) {
        jpa.deleteById(configId);
        jpa.flush();
    }

    @Override
    public void deleteByUser(UUID userId) {
        jpa.deleteAll(jpa.findByUserId(userId));
        jpa.flush();
    }

    private static List<ProviderConfig> toDomain(List<ProviderConfigEntity> entities) {
        return entities.stream().map(ProviderConfigMapper::toDomain).toList();
    }
}

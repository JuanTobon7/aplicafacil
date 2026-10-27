package com.aplicafacil.identity.infrastructure.persistence;

import java.time.Instant;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.aplicafacil.identity.application.port.out.LoginCodeRepository;
import com.aplicafacil.identity.domain.LoginCode;
import com.aplicafacil.identity.infrastructure.persistence.entity.LoginCodeEntity;

/** Adaptador JPA de {@link LoginCodeRepository}. */
@Repository
public class JpaLoginCodeRepository implements LoginCodeRepository {

    private final SpringDataLoginCodeRepository jpa;

    public JpaLoginCodeRepository(SpringDataLoginCodeRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public void save(LoginCode code) {
        jpa.save(LoginCodeEntity.builder()
                .codeHash(code.codeHash())
                .userId(code.userId())
                .attemptId(code.attemptId())
                .expiresAt(code.expiresAt())
                .consumedAt(code.consumedAt())
                .build());
    }

    @Override
    public Optional<LoginCode> findByHash(String codeHash) {
        return jpa.findById(codeHash).map(e ->
                new LoginCode(e.getCodeHash(), e.getUserId(), e.getAttemptId(), e.getExpiresAt(), e.getConsumedAt()));
    }

    @Override
    public void markConsumed(String codeHash, Instant now) {
        jpa.findById(codeHash).ifPresent(e -> e.setConsumedAt(now));
    }

    @Override
    public void deleteExpired(Instant now) {
        jpa.deleteExpired(now);
    }
}

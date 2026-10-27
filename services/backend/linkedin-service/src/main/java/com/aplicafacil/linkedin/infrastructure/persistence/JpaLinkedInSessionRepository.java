package com.aplicafacil.linkedin.infrastructure.persistence;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.aplicafacil.linkedin.application.port.out.LinkedInSessionRepository;
import com.aplicafacil.linkedin.infrastructure.persistence.entity.LinkedInSessionEntity;
import com.aplicafacil.platform.crypto.EncryptedPayload;
import com.aplicafacil.platform.crypto.SessionCryptoService;

/**
 * Único punto de lectura y escritura de {@code linkedin_session}. Cifra al guardar y descifra al leer: el
 * storageState en claro nunca se escribe en disco ni en logs.
 */
@Repository
public class JpaLinkedInSessionRepository implements LinkedInSessionRepository {

    private final SpringDataLinkedInSessionRepository sessions;
    private final SessionCryptoService crypto;
    private final Clock clock;

    public JpaLinkedInSessionRepository(SpringDataLinkedInSessionRepository sessions, SessionCryptoService crypto,
                                        Clock clock) {
        this.sessions = sessions;
        this.crypto = crypto;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<String> loadStorageState(UUID userId) {
        return sessions.findByUserId(userId).map(s -> crypto.decrypt(
                new EncryptedPayload(s.getStorageStateEncrypted(), s.getIv(), s.getKeyVersion())));
    }

    @Override
    @Transactional
    public void saveStorageState(UUID userId, String storageStateJson) {
        Instant now = clock.instant();
        EncryptedPayload payload = crypto.encrypt(storageStateJson);
        LinkedInSessionEntity session = sessions.findByUserId(userId).orElseGet(() -> LinkedInSessionEntity.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .createdAt(now)
                .build());
        session.setStorageStateEncrypted(payload.ciphertext());
        session.setIv(payload.iv());
        session.setKeyVersion(payload.keyVersion());
        session.setUpdatedAt(now);
        session.setLastValidatedAt(now);
        sessions.save(session);
    }

    @Override
    @Transactional
    public void markValidated(UUID userId, Instant validatedAt) {
        sessions.findByUserId(userId).ifPresent(s -> s.setLastValidatedAt(validatedAt));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Instant> lastValidatedAt(UUID userId) {
        return sessions.findByUserId(userId).map(LinkedInSessionEntity::getLastValidatedAt);
    }

    @Override
    @Transactional
    public void delete(UUID userId) {
        sessions.deleteByUserId(userId);
    }

    @Override
    @Transactional
    public Optional<SessionLease> tryAcquireLease(UUID userId, Duration duration) {
        Instant now = clock.instant();
        UUID owner = UUID.randomUUID();
        Instant expiresAt = now.plus(duration);
        return sessions.acquireLease(userId, owner, expiresAt, now) == 1
                ? Optional.of(new SessionLease(userId, owner, expiresAt))
                : Optional.empty();
    }

    @Override
    @Transactional
    public void releaseLease(SessionLease lease) {
        sessions.releaseLease(lease.userId(), lease.owner());
    }

    @Override
    @Transactional(readOnly = true)
    public boolean exists(UUID userId) {
        return sessions.findByUserId(userId).isPresent();
    }
}

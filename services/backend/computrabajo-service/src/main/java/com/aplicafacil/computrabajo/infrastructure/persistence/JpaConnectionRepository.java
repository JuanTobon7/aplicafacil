package com.aplicafacil.computrabajo.infrastructure.persistence;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.aplicafacil.computrabajo.application.exception.SessionUnreadableException;
import com.aplicafacil.computrabajo.application.port.out.ConnectionRepository;
import com.aplicafacil.computrabajo.domain.ComputrabajoConnection;
import com.aplicafacil.computrabajo.infrastructure.persistence.entity.ComputrabajoSessionEntity;
import com.aplicafacil.platform.crypto.EncryptedPayload;
import com.aplicafacil.platform.crypto.SessionCryptoService;
import com.aplicafacil.platform.crypto.SessionDecryptionException;

/**
 * Único punto de lectura y escritura de {@code computrabajo_session}. Cifra al guardar y descifra al leer: el
 * storageState en claro nunca se escribe en disco ni en logs.
 */
@Repository
public class JpaConnectionRepository implements ConnectionRepository {

    private final SpringDataComputrabajoSessionRepository rows;
    private final SessionCryptoService crypto;
    private final Clock clock;

    public JpaConnectionRepository(SpringDataComputrabajoSessionRepository rows, SessionCryptoService crypto,
                                   Clock clock) {
        this.rows = rows;
        this.crypto = crypto;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ComputrabajoConnection> find(UUID userId) {
        return rows.findById(userId).map(JpaConnectionRepository::toDomain);
    }

    @Override
    @Transactional
    public void save(ComputrabajoConnection connection, String storageState) {
        ComputrabajoSessionEntity row = rows.findById(connection.userId()).orElseGet(() -> {
            ComputrabajoSessionEntity created = new ComputrabajoSessionEntity();
            created.setUserId(connection.userId());
            return created;
        });
        EncryptedPayload payload = crypto.encrypt(storageState);
        row.setStorageStateEncrypted(payload.ciphertext());
        row.setIv(payload.iv());
        row.setKeyVersion(payload.keyVersion());
        copyState(connection, row);
        rows.save(row);
    }

    @Override
    @Transactional
    public void update(ComputrabajoConnection connection) {
        rows.findById(connection.userId()).ifPresent(row -> copyState(connection, row));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<String> loadStorageState(UUID userId) {
        return rows.findById(userId).map(row -> {
            try {
                return crypto.decrypt(new EncryptedPayload(row.getStorageStateEncrypted(), row.getIv(),
                        row.getKeyVersion()));
            } catch (SessionDecryptionException e) {
                throw new SessionUnreadableException(e);
            }
        });
    }

    @Override
    @Transactional
    public void delete(UUID userId) {
        rows.deleteById(userId);
    }

    private void copyState(ComputrabajoConnection connection, ComputrabajoSessionEntity row) {
        row.setStatus(connection.status());
        row.setCandidateName(connection.candidateName());
        row.setConnectedAt(connection.connectedAt());
        row.setLastValidatedAt(connection.lastValidatedAt());
        row.setUpdatedAt(clock.instant());
    }

    private static ComputrabajoConnection toDomain(ComputrabajoSessionEntity row) {
        return new ComputrabajoConnection(row.getUserId(), row.getStatus(), row.getCandidateName(),
                row.getConnectedAt(), row.getLastValidatedAt());
    }
}

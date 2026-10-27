package com.aplicafacil.aiconfig.infrastructure.persistence;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Repository;

import com.aplicafacil.aiconfig.application.exception.AiConfigException;
import com.aplicafacil.aiconfig.application.port.out.CredentialVault;
import com.aplicafacil.aiconfig.infrastructure.persistence.entity.ProviderCredentialEntity;
import com.aplicafacil.platform.crypto.EncryptedPayload;
import com.aplicafacil.platform.crypto.SessionCryptoService;
import com.aplicafacil.platform.crypto.SessionDecryptionException;

/** {@link CredentialVault} sobre {@code provider_credential}, cifrado con AES-GCM ({@link SessionCryptoService}). */
@Repository
public class JpaCredentialVault implements CredentialVault {

    /** Keys muy cortas no muestran pista: revelaría casi toda la key. */
    private static final int MIN_LENGTH_FOR_HINT = 9;
    private static final int HINT_LENGTH = 4;

    private final SpringDataProviderCredentialRepository jpa;
    private final SessionCryptoService crypto;

    public JpaCredentialVault(SpringDataProviderCredentialRepository jpa, SessionCryptoService crypto) {
        this.jpa = jpa;
        this.crypto = crypto;
    }

    @Override
    public StoredCredential store(UUID userId, String apiKey, Instant now) {
        String key = apiKey.strip();
        EncryptedPayload encrypted = crypto.encrypt(key);
        String hint = key.length() < MIN_LENGTH_FOR_HINT ? "" : key.substring(key.length() - HINT_LENGTH);
        ProviderCredentialEntity saved = jpa.save(ProviderCredentialEntity.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .secretEncrypted(encrypted.ciphertext())
                .iv(encrypted.iv())
                .keyVersion(encrypted.keyVersion())
                .hint(hint)
                .createdAt(now)
                .build());
        return new StoredCredential(saved.getId(), hint);
    }

    @Override
    public Optional<String> hint(UUID credentialId) {
        return jpa.findById(credentialId).map(ProviderCredentialEntity::getHint);
    }

    @Override
    public Map<UUID, String> hints(Collection<UUID> credentialIds) {
        return jpa.findAllById(credentialIds).stream()
                .collect(Collectors.toMap(ProviderCredentialEntity::getId, ProviderCredentialEntity::getHint));
    }

    @Override
    public String reveal(UUID credentialId) {
        ProviderCredentialEntity credential = jpa.findById(credentialId)
                .orElseThrow(AiConfigException::configNotFound);
        try {
            return crypto.decrypt(new EncryptedPayload(credential.getSecretEncrypted(), credential.getIv(),
                    credential.getKeyVersion()));
        } catch (SessionDecryptionException e) {
            throw AiConfigException.credentialUnreadable();
        }
    }

    @Override
    public void delete(UUID credentialId) {
        jpa.deleteById(credentialId);
    }

    @Override
    public void deleteByUser(UUID userId) {
        jpa.deleteByUserId(userId);
    }
}

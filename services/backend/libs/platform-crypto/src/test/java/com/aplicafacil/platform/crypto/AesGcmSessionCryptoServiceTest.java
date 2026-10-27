package com.aplicafacil.platform.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.SecureRandom;
import java.util.Base64;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class AesGcmSessionCryptoServiceTest {

    private static final String STORAGE_STATE =
            "{\"cookies\":[{\"name\":\"li_at\",\"value\":\"secreto\",\"domain\":\".linkedin.com\"}],\"origins\":[]}";

    private final String key = randomKey();
    private final SessionCryptoService crypto = new AesGcmSessionCryptoService(key, 1);

    @Test
    void decryptsWhatItEncrypted() {
        EncryptedPayload payload = crypto.encrypt(STORAGE_STATE);

        assertThat(crypto.decrypt(payload)).isEqualTo(STORAGE_STATE);
        assertThat(payload.keyVersion()).isEqualTo(1);
        assertThat(payload.iv()).hasSize(12);
        assertThat(new String(payload.ciphertext())).doesNotContain("li_at", "secreto");
    }

    @Test
    void usesAFreshIvForEveryWrite() {
        EncryptedPayload first = crypto.encrypt(STORAGE_STATE);
        EncryptedPayload second = crypto.encrypt(STORAGE_STATE);

        assertThat(first.iv()).isNotEqualTo(second.iv());
        assertThat(first.ciphertext()).isNotEqualTo(second.ciphertext());
    }

    @Test
    void rejectsADifferentKey() {
        EncryptedPayload payload = crypto.encrypt(STORAGE_STATE);
        SessionCryptoService otherKey = new AesGcmSessionCryptoService(randomKey(), 1);

        assertThatThrownBy(() -> otherKey.decrypt(payload))
                .isInstanceOf(SessionDecryptionException.class)
                .hasMessageNotContaining("secreto");
    }

    @Test
    void rejectsTamperedCiphertext() {
        EncryptedPayload payload = crypto.encrypt(STORAGE_STATE);
        payload.ciphertext()[0] ^= 1;

        assertThatThrownBy(() -> crypto.decrypt(payload)).isInstanceOf(SessionDecryptionException.class);
    }

    @Test
    void rejectsAnUnknownKeyVersion() {
        EncryptedPayload payload = crypto.encrypt(STORAGE_STATE);
        EncryptedPayload fromVersion2 = new EncryptedPayload(payload.ciphertext(), payload.iv(), 2);

        assertThatThrownBy(() -> crypto.decrypt(fromVersion2)).isInstanceOf(SessionDecryptionException.class);
    }

    @Test
    void neverPrintsTheContent() {
        assertThat(crypto.encrypt(STORAGE_STATE).toString()).doesNotContain("li_at");
        assertThat(new SessionEncryptionProperties(key, 1).toString()).doesNotContain(key);
    }

    @Test
    void refusesMissingShortOrInvalidKeys() {
        assertThatThrownBy(() -> new AesGcmSessionCryptoService("", 1))
                .isInstanceOf(InvalidEncryptionKeyException.class)
                .hasMessageContaining("openssl rand -base64 32");
        assertThatThrownBy(() -> new AesGcmSessionCryptoService(Base64.getEncoder().encodeToString(new byte[16]), 1))
                .isInstanceOf(InvalidEncryptionKeyException.class)
                .hasMessageContaining("32 bytes");
        assertThatThrownBy(() -> new AesGcmSessionCryptoService("no-es-base64!!", 1))
                .isInstanceOf(InvalidEncryptionKeyException.class);
    }

    @Test
    void contextFailsToStartWithoutKey() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(SessionCryptoAutoConfiguration.class))
                .run(context -> assertThat(context).hasFailed()
                        .getFailure().rootCause().isInstanceOf(InvalidEncryptionKeyException.class));
    }

    @Test
    void contextStartsWithAValidKey() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(SessionCryptoAutoConfiguration.class))
                .withPropertyValues("aplicafacil.session-encryption.key=" + key)
                .run(context -> assertThat(context).hasSingleBean(SessionCryptoService.class));
    }

    private static String randomKey() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }
}

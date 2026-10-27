package com.aplicafacil.platform.crypto;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * AES-256-GCM con un IV aleatorio de 12 bytes en cada cifrado y un tag de 128 bits.
 *
 * <p>Las llaves se indexan por versión: hoy existe solo la actual, pero rotar consistirá en agregar
 * la versión anterior para descifrar y volver a cifrar con la nueva.
 */
public class AesGcmSessionCryptoService implements SessionCryptoService {

    static final int KEY_BYTES = 32;
    static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final int currentVersion;
    private final Map<Integer, SecretKey> keys;

    /**
     * @throws InvalidEncryptionKeyException si la llave falta, no es Base64 o no mide 32 bytes
     */
    public AesGcmSessionCryptoService(String base64Key, int keyVersion) {
        this.currentVersion = keyVersion;
        this.keys = Map.of(keyVersion, parseKey(base64Key));
    }

    @Override
    public EncryptedPayload encrypt(String plaintext) {
        byte[] iv = new byte[IV_BYTES];
        RANDOM.nextBytes(iv);
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, keys.get(currentVersion), new GCMParameterSpec(TAG_BITS, iv));
            return new EncryptedPayload(cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8)), iv, currentVersion);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("No se pudo cifrar", e);
        }
    }

    @Override
    public String decrypt(EncryptedPayload payload) {
        SecretKey key = keys.get(payload.keyVersion());
        if (key == null) {
            throw new SessionDecryptionException("No hay llave para key_version=" + payload.keyVersion(), null);
        }
        if (payload.iv() == null || payload.iv().length != IV_BYTES) {
            throw new SessionDecryptionException("IV inválido", null);
        }
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, payload.iv()));
            return new String(cipher.doFinal(payload.ciphertext()), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            // AEADBadTagException: la llave cambió o el dato fue alterado
            throw new SessionDecryptionException("No se pudo descifrar (¿cambió SESSION_ENCRYPTION_KEY?)", e);
        }
    }

    @Override
    public int currentKeyVersion() {
        return currentVersion;
    }

    private static SecretKey parseKey(String base64Key) {
        if (base64Key == null || base64Key.isBlank()) {
            throw new InvalidEncryptionKeyException("SESSION_ENCRYPTION_KEY no está definida.");
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(base64Key.strip());
        } catch (IllegalArgumentException e) {
            throw new InvalidEncryptionKeyException("SESSION_ENCRYPTION_KEY no es Base64 válido.");
        }
        if (bytes.length != KEY_BYTES) {
            throw new InvalidEncryptionKeyException(
                    "SESSION_ENCRYPTION_KEY debe tener 32 bytes (tiene " + bytes.length + ").");
        }
        return new SecretKeySpec(bytes, "AES");
    }
}

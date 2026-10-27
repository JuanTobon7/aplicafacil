package com.aplicafacil.platform.crypto;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param key        Base64 de 32 bytes ({@code SESSION_ENCRYPTION_KEY})
 * @param keyVersion versión que se guarda junto a cada dato cifrado
 */
@ConfigurationProperties("aplicafacil.session-encryption")
public record SessionEncryptionProperties(String key, Integer keyVersion) {

    public SessionEncryptionProperties {
        if (keyVersion == null) {
            keyVersion = 1;
        }
    }

    /** Oculta la llave: el toString por defecto de un record incluye todos los campos. */
    @Override
    public String toString() {
        return "SessionEncryptionProperties[keyVersion=" + keyVersion + "]";
    }
}

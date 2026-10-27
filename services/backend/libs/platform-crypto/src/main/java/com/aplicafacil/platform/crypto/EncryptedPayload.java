package com.aplicafacil.platform.crypto;

/**
 * Resultado de cifrar. Se guarda tal cual en la BD (columnas {@code *_encrypted}, {@code iv}, {@code key_version}).
 *
 * @param ciphertext texto cifrado + tag GCM de 128 bits
 * @param iv         12 bytes aleatorios, distintos en cada escritura
 * @param keyVersion versión de la llave con la que se cifró (para rotarla en el futuro)
 */
public record EncryptedPayload(byte[] ciphertext, byte[] iv, int keyVersion) {

    /** Nunca imprime el contenido: el toString por defecto de un record incluye todos los campos. */
    @Override
    public String toString() {
        return "EncryptedPayload[keyVersion=" + keyVersion + ", " + ciphertext.length + " bytes]";
    }
}

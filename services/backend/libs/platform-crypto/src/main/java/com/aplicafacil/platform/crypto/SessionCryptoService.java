package com.aplicafacil.platform.crypto;

/**
 * Único punto de cifrado de secretos en reposo: el storageState de Playwright, los tokens OAuth de
 * LinkedIn y las llaves privadas de firma. Usa AES-256-GCM con un IV aleatorio de 12 bytes en cada escritura.
 *
 * <p>Reglas: el texto plano solo vive en memoria. Ninguna implementación lo escribe en disco
 * ni en logs, y las excepciones nunca lo incluyen en el mensaje.
 */
public interface SessionCryptoService {

    EncryptedPayload encrypt(String plaintext);

    /**
     * @throws SessionDecryptionException si la llave cambió, la versión no existe o el dato fue
     *         alterado. Quien la recibe marca la sesión como EXPIRED y pide reconectar.
     */
    String decrypt(EncryptedPayload payload);

    /** Versión de la llave con la que se cifra actualmente. */
    int currentKeyVersion();
}

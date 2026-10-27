package com.aplicafacil.platform.crypto;

/** No se pudo descifrar el dato (llave cambiada o datos alterados). El mensaje nunca incluye el contenido. */
public class SessionDecryptionException extends RuntimeException {

    public SessionDecryptionException(String message, Throwable cause) {
        super(message, cause);
    }
}

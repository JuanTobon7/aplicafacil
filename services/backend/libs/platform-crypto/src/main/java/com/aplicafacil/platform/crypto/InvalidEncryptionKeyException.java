package com.aplicafacil.platform.crypto;

/** La llave falta o es inválida. El servicio no arranca, y el mensaje explica cómo generar una llave. */
public class InvalidEncryptionKeyException extends RuntimeException {

    static final String HOW_TO = """

            Genera una llave de 32 bytes en Base64 y guárdala en infra/.env como SESSION_ENCRYPTION_KEY:
              openssl rand -base64 32
              powershell -c "[Convert]::ToBase64String((1..32|%{[byte](Get-Random -Max 256)}))"
            (dev-up.ps1 la genera sola si está vacía). Si la cambias, las sesiones guardadas quedan
            EXPIRED y hay que reconectar LinkedIn.""";

    public InvalidEncryptionKeyException(String problem) {
        super(problem + HOW_TO);
    }
}

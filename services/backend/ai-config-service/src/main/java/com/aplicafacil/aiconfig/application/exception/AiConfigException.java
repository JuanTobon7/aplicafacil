package com.aplicafacil.aiconfig.application.exception;

import org.springframework.http.HttpStatus;

/** Error con un {@code code} estable que el front traduce a mensaje y acción. */
public class AiConfigException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public AiConfigException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }

    /** También cuando existe pero es de otra persona: no se revela que existe. */
    public static AiConfigException configNotFound() {
        return new AiConfigException(HttpStatus.NOT_FOUND, "CONFIG_NOT_FOUND", "La configuración no existe.");
    }

    public static AiConfigException requestNotFound() {
        return new AiConfigException(HttpStatus.NOT_FOUND, "REQUEST_NOT_FOUND", "La solicitud no existe.");
    }

    public static AiConfigException unknownProviderType(String type) {
        return new AiConfigException(HttpStatus.BAD_REQUEST, "UNKNOWN_PROVIDER_TYPE",
                "Tipo de proveedor no soportado: " + type);
    }

    public static AiConfigException baseUrlNotAllowed() {
        return new AiConfigException(HttpStatus.BAD_REQUEST, "BASE_URL_NOT_ALLOWED",
                "Este proveedor no admite una URL personalizada.");
    }

    public static AiConfigException invalidBaseUrl() {
        return new AiConfigException(HttpStatus.BAD_REQUEST, "INVALID_BASE_URL",
                "La URL debe usar https (o http solo para 127.0.0.1 / localhost).");
    }

    public static AiConfigException duplicateName() {
        return new AiConfigException(HttpStatus.CONFLICT, "DUPLICATE_NAME", "Ya tienes una configuración con ese nombre.");
    }

    public static AiConfigException tooManyConfigs(int max) {
        return new AiConfigException(HttpStatus.CONFLICT, "TOO_MANY_CONFIGS",
                "Alcanzaste el máximo de " + max + " configuraciones.");
    }

    public static AiConfigException versionConflict() {
        return new AiConfigException(HttpStatus.CONFLICT, "VERSION_CONFLICT",
                "La configuración cambió mientras la editabas. Recarga y vuelve a intentarlo.");
    }

    public static AiConfigException configDisabled() {
        return new AiConfigException(HttpStatus.CONFLICT, "CONFIG_DISABLED", "La configuración está desactivada.");
    }

    /** La llave de cifrado cambió: la persona debe volver a ingresar la API key. */
    public static AiConfigException credentialUnreadable() {
        return new AiConfigException(HttpStatus.CONFLICT, "CREDENTIAL_UNREADABLE",
                "No se pudo descifrar la API key. Vuelve a ingresarla.");
    }

    public static AiConfigException forbiddenReader() {
        return new AiConfigException(HttpStatus.FORBIDDEN, "FORBIDDEN_READER",
                "Este servicio no puede leer configuraciones de IA.");
    }
}

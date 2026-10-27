package com.aplicafacil.linkedin.domain;

/** Paso actual de un intento de conexión. Se informa por SSE mientras el estado es CONNECTING. */
public enum ConnectStep {
    OPENING_BROWSER("Abriendo LinkedIn"),
    WAITING_FOR_LOGIN("Esperando inicio de sesión"),
    READING_PROFILE("Leyendo tu perfil"),
    CREATING_ACCOUNT("Creando tu cuenta"),
    SAVING_SESSION("Guardando tu sesión cifrada");

    private final String label;

    ConnectStep(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}

package com.aplicafacil.computrabajo.domain;

/** Pasos del inicio de sesión: los tres primeros los informa el scraper, el último es de este servicio. */
public enum ConnectStep {
    OPENING_BROWSER("Abriendo Computrabajo"),
    WAITING_FOR_LOGIN("Esperando inicio de sesión"),
    READING_PROFILE("Leyendo tu cuenta"),
    SAVING_SESSION("Guardando tu sesión cifrada");

    private final String label;

    ConnectStep(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}

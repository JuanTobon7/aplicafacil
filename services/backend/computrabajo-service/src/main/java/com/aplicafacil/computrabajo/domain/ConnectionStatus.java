package com.aplicafacil.computrabajo.domain;

/** Sin fila no hay conexión (NOT_CONNECTED en la API). */
public enum ConnectionStatus {
    CONNECTED,
    /** Computrabajo cerró la sesión: hay que iniciarla de nuevo. */
    EXPIRED
}

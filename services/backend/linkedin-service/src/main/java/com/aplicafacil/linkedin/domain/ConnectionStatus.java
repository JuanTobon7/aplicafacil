package com.aplicafacil.linkedin.domain;

/** Estado de la conexión con LinkedIn, tal como lo ve el front ({@code GET /api/linkedin/status}). */
public enum ConnectionStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    /** La sesión venció o no se pudo descifrar (llave cambiada). Hay que reconectar. */
    EXPIRED
}

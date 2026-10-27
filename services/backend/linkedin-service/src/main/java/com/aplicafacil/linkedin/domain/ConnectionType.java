package com.aplicafacil.linkedin.domain;

/** Cómo está conectada la cuenta. */
public enum ConnectionType {
    /** Fase local: storageState de Playwright cifrado en la BD. */
    SESSION,
    /** Fase desplegada: solo identidad por OIDC (no permite postular). */
    OAUTH,
    /** Fase desplegada: la extensión del navegador ejecuta las acciones; el servidor no guarda cookies. */
    EXTENSION
}

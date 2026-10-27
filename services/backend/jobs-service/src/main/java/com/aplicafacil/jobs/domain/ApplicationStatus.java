package com.aplicafacil.jobs.domain;

/** Ciclo de vida de un intento de postulación. */
public enum ApplicationStatus {
    /** Formulario listo; espera la confirmación del usuario antes de enviar. */
    PENDING_CONFIRMATION,
    IN_PROGRESS,
    SUBMITTED,
    FAILED,
    CANCELLED,
    /** No se intentó (límite diario, ya postulado, preguntas sin respuesta). */
    SKIPPED
}

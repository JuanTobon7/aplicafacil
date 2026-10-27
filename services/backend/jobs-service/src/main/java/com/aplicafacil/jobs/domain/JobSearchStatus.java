package com.aplicafacil.jobs.domain;

/**
 * Ciclo de vida de una búsqueda: {@code QUEUED → RUNNING → COMPLETED | FAILED}. Un fallo transitorio la devuelve
 * a {@code QUEUED} con una espera hasta agotar los intentos.
 */
public enum JobSearchStatus {
    QUEUED,
    RUNNING,
    COMPLETED,
    FAILED
}

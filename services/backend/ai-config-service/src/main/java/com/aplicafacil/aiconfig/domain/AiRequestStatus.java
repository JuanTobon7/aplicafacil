package com.aplicafacil.aiconfig.domain;

/** Estado de una solicitud de IA asíncrona. Solo {@code PENDING} puede cambiar. */
public enum AiRequestStatus {
    PENDING,
    COMPLETED,
    FAILED,
    TIMED_OUT;

    public boolean isFinal() {
        return this != PENDING;
    }
}

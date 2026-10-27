package com.aplicafacil.profile.application.ai;

import java.time.Duration;

/**
 * Límites de las tareas de IA de profile-service (los arma infrastructure desde la configuración).
 *
 * @param timeout       cuánto se espera el resultado antes de cerrar la tarea como TIMED_OUT
 * @param maxCvChars    texto del CV que se manda como mucho (el contrato acepta 32 000 por mensaje)
 * @param maxFileBytes  tamaño máximo del archivo del CV
 */
public record ProfileAiPolicy(Duration timeout, int maxCvChars, long maxFileBytes) {

    public static final int CONTRACT_MAX_CHARS = 30_000;

    public ProfileAiPolicy {
        if (timeout == null || timeout.isNegative() || timeout.isZero()) {
            timeout = Duration.ofMinutes(3);
        }
        maxCvChars = maxCvChars <= 0 ? 24_000 : Math.min(maxCvChars, CONTRACT_MAX_CHARS);
        maxFileBytes = maxFileBytes <= 0 ? 10L * 1024 * 1024 : maxFileBytes;
    }
}

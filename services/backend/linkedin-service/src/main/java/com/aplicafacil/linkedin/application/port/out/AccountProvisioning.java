package com.aplicafacil.linkedin.application.port.out;

import java.util.UUID;

import com.aplicafacil.linkedin.contract.LinkedInIdentity;

/**
 * Crea, actualiza o vincula el usuario de una identidad de LinkedIn (identity-service). Si el servicio no responde,
 * la implementación lanza {@code ConnectException.identityServiceUnavailable}.
 */
public interface AccountProvisioning {

    Provisioned provision(UUID attemptId, LinkedInIdentity identity);

    /** @param loginCode de un solo uso: viaja al front por SSE y nunca se loguea */
    record Provisioned(UUID userId, String outcome, String loginCode) {

        @Override
        public String toString() {
            return "Provisioned[userId=" + userId + ", outcome=" + outcome + "]";
        }
    }
}

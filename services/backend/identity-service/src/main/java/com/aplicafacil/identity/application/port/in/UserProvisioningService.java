package com.aplicafacil.identity.application.port.in;

import java.util.UUID;

import com.aplicafacil.linkedin.contract.LinkedInIdentity;

/**
 * Único punto que decide si una identidad de LinkedIn <b>crea</b>, <b>actualiza</b> o <b>vincula</b>
 * un usuario, venga de una sesión de Playwright o de OIDC.
 *
 * <p>Orden de búsqueda:
 * <ol>
 *   <li>Identificador principal del proveedor ({@code memberUrn} para SESSION, {@code oidcSub} para OIDC).</li>
 *   <li>El otro identificador, si la identidad lo trae.</li>
 *   <li>Correo, solo si está verificado (vincula una cuenta creada en el otro modo).</li>
 *   <li>Si no hay coincidencia, se crea un usuario nuevo.</li>
 * </ol>
 * Los campos editados a mano por el usuario nunca se sobrescriben.
 */
public interface UserProvisioningService {

    ProvisioningResult provision(LinkedInIdentity identity);

    enum Outcome { CREATED, UPDATED, LINKED }

    /** @param userId id estable del usuario ({@code sub} de los JWT propios) */
    record ProvisioningResult(UUID userId, Outcome outcome) {
    }
}

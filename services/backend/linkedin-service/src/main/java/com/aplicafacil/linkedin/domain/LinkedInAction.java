package com.aplicafacil.linkedin.domain;

import java.util.List;

import com.aplicafacil.linkedin.contract.ProfileSnapshot;

/**
 * Acción que se ejecuta con la sesión de un usuario. Es serializable a propósito, para poder
 * enviarla a la extensión del navegador en la fase desplegada.
 *
 * @param <R> resultado de la acción
 */
public sealed interface LinkedInAction<R>
        permits LinkedInAction.ValidateSession, LinkedInAction.ReadProfile, LinkedInAction.SearchJobs {

    /** Comprueba que la sesión siga iniciada (headless). */
    record ValidateSession() implements LinkedInAction<SessionCheck> {
    }

    /** "Sincronizar de nuevo": vuelve a leer el perfil completo. */
    record ReadProfile() implements LinkedInAction<ProfileSnapshot> {
    }

    /** Busca ofertas en LinkedIn y recorre las cards del listado (lo pide el worker de jobs-service). */
    record SearchJobs(JobSearchCriteria criteria) implements LinkedInAction<List<JobListing>> {
    }

    enum SessionCheck { VALID, EXPIRED }
}

package com.aplicafacil.linkedin.application.port.out;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import com.aplicafacil.linkedin.contract.LinkedInIdentity;
import com.aplicafacil.linkedin.contract.ProfileSnapshot;
import com.aplicafacil.linkedin.domain.ConnectStep;
import com.aplicafacil.linkedin.domain.JobListing;
import com.aplicafacil.linkedin.domain.JobSearchCriteria;

/**
 * Puerto hacia quien maneja el navegador. linkedin-service es el proxy: decide cuándo, para quién y con qué
 * sesión, guarda el resultado cifrado y aplica el bloqueo por usuario. La implementación (el scraper NestJS
 * con Playwright) no tiene base de datos: recibe el storageState en cada llamada y devuelve el renovado.
 *
 * <p>Contrato HTTP: {@code contracts/openapi/scraper-browser.yaml}.
 */
public interface BrowserAutomation {

    enum BrowserState { INSTALLING, READY, FAILED, UNAVAILABLE }

    BrowserState browserState();

    /**
     * Abre el navegador visible y espera a que la persona inicie sesión. Bloquea hasta terminar.
     *
     * @param onStep avisos de progreso para el SSE del front
     * @throws com.aplicafacil.linkedin.application.exception.ConnectException con el código del fallo
     */
    LoginResult login(UUID attemptId, Duration timeout, Consumer<ConnectStep> onStep);

    void cancelLogin(UUID attemptId);

    /** Valida una sesión en un contexto headless no persistente. */
    SessionResult validate(String storageState);

    /** "Sincronizar de nuevo": lee el perfil con la sesión. */
    ProfileResult readProfile(String storageState);

    /** Busca ofertas con la sesión y recorre las cards del listado (headless, contexto no persistente). */
    JobSearchResult searchJobs(String storageState, JobSearchCriteria criteria);

    /** @param storageState renovado; {@code null} si {@code valid = false} */
    record JobSearchResult(boolean valid, List<JobListing> jobs, String storageState) {
        @Override
        public String toString() {
            return "JobSearchResult[valid=" + valid + ", jobs=" + (jobs == null ? 0 : jobs.size()) + "]";
        }
    }

    /** {@code storageState} es secreto: los toString lo ocultan. */
    record LoginResult(LinkedInIdentity identity, ProfileSnapshot profile, String storageState) {
        @Override
        public String toString() {
            return "LoginResult[memberUrn=" + identity.memberUrn() + "]";
        }
    }

    /** @param storageState renovado; {@code null} si {@code valid = false} */
    record SessionResult(boolean valid, String storageState) {
        @Override
        public String toString() {
            return "SessionResult[valid=" + valid + "]";
        }
    }

    record ProfileResult(boolean valid, ProfileSnapshot profile, String storageState) {
        @Override
        public String toString() {
            return "ProfileResult[valid=" + valid + "]";
        }
    }
}

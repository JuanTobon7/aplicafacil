package com.aplicafacil.automation.application.port;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Llena y envía una postulación en un proveedor concreto: LinkedIn Easy Apply o un ATS externo
 * (Greenhouse, Lever, Workday). Los datos salen del perfil del usuario y la sesión de LinkedIn se usa
 * solo a través de linkedin-service ({@code LinkedInSessionProvider}).
 *
 * <p>El flujo tiene dos pasos para que el usuario confirme antes de enviar:
 * {@link #prepare} llena el formulario sin enviarlo y {@link #submit} lo envía después de la confirmación.
 */
public interface ApplicationProvider {

    /** Coincide con {@code job_application.provider}. */
    String type();

    boolean supports(URI jobUrl);

    PreparedApplication prepare(UUID userId, URI jobUrl);

    SubmissionResult submit(PreparedApplication prepared);

    /**
     * @param answers    respuestas propuestas por campo del formulario, para que el usuario las revise
     * @param unanswered preguntas que no se pudieron responder con el perfil
     */
    record PreparedApplication(UUID applicationId, UUID userId, URI jobUrl, String company, String jobTitle,
                               Map<String, String> answers, List<String> unanswered) {
    }

    record SubmissionResult(boolean submitted, String errorCode, String errorMessage) {
    }
}

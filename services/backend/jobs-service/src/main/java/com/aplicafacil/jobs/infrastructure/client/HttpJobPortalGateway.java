package com.aplicafacil.jobs.infrastructure.client;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.aplicafacil.jobs.application.port.out.JobPortalException;
import com.aplicafacil.jobs.application.port.out.JobPortalGateway;
import com.aplicafacil.jobs.domain.JobCard;
import com.aplicafacil.jobs.domain.SearchCriteria;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Template Method común a los gateways HTTP: armar el cliente con token de servicio y timeouts largos, enviar,
 * leer {@code jobs} y traducir los errores ({@code {code, message}}) a {@link JobPortalException} con la misma
 * política de reintento para todos. Cada portal solo dice a qué URL y con qué cuerpo.
 */
abstract class HttpJobPortalGateway implements JobPortalGateway {

    /** Códigos con los que reintentar no sirve: hace falta que la persona actúe (conectar, reconectar…). */
    private static final Set<String> PERMANENT = Set.of("NOT_CONNECTED", "SESSION_EXPIRED", "SESSION_REQUIRED",
            "INVALID_REQUEST", "UNKNOWN_PORTAL");

    private final RestClient client;
    private final JsonMapper json;

    protected HttpJobPortalGateway(RestClient.Builder builder, String baseUrl, Duration timeout,
                                   ServiceTokenProvider tokens, JsonMapper json) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(timeout);
        this.client = builder.clone().baseUrl(baseUrl).requestFactory(factory)
                .requestInterceptor(tokens.interceptor()).build();
        this.json = json;
    }

    /** Ruta del endpoint de búsqueda para este usuario. */
    protected abstract String path(UUID userId);

    /** Cuerpo de la petición a partir de los criterios. */
    protected abstract ObjectNode body(SearchCriteria criteria, JsonMapper json);

    @Override
    public final List<JobCard> search(UUID userId, SearchCriteria criteria) {
        JsonNode response;
        try {
            response = client.post().uri(path(userId)).contentType(MediaType.APPLICATION_JSON)
                    .body(body(criteria, json)).retrieve().body(JsonNode.class);
        } catch (ResourceAccessException e) {
            throw new JobPortalException("PORTAL_UNAVAILABLE",
                    "No responde el servicio que maneja el navegador para " + platform() + ".", true, e);
        } catch (RestClientResponseException e) {
            throw translate(e);
        }
        if (response == null || !response.path("jobs").isArray()) {
            throw new JobPortalException("INVALID_RESPONSE", "Respuesta inválida al buscar en " + platform() + ".", true);
        }
        return response.path("jobs").valueStream().map(job -> json.treeToValue(job, JobCard.class)).toList();
    }

    protected static ObjectNode criteria(ObjectNode target, SearchCriteria criteria) {
        target.put("keywords", criteria.keywords()).put("maxResults", criteria.maxResults());
        if (criteria.location() != null) {
            target.put("location", criteria.location());
        }
        if (criteria.postedWithinDays() != null) {
            target.put("postedWithinDays", criteria.postedWithinDays());
        }
        if (criteria.remote()) {
            target.put("remote", true);
        }
        return target;
    }

    private JobPortalException translate(RestClientResponseException e) {
        JsonNode error;
        try {
            error = json.readTree(e.getResponseBodyAsString());
        } catch (RuntimeException parse) {
            error = json.createObjectNode();
        }
        String code = error.path("code").asString("HTTP_" + e.getStatusCode().value());
        String message = error.path("detail").asString(error.path("message").asString("El portal respondió "
                + e.getStatusCode().value() + "."));
        boolean retryable = !PERMANENT.contains(code) && (e.getStatusCode().is5xxServerError()
                || e.getStatusCode().value() == 409 || e.getStatusCode().value() == 429);
        return new JobPortalException(code, message, retryable, e);
    }
}

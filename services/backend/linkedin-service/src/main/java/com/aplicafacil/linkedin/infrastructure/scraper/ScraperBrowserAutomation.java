package com.aplicafacil.linkedin.infrastructure.scraper;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.aplicafacil.linkedin.application.exception.ConnectException;
import com.aplicafacil.linkedin.application.port.out.BrowserAutomation;
import com.aplicafacil.linkedin.contract.ProfileSnapshot;
import com.aplicafacil.linkedin.domain.ConnectStep;
import com.aplicafacil.linkedin.domain.JobListing;
import com.aplicafacil.linkedin.domain.JobSearchCriteria;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Adaptador HTTP hacia el scraper (NestJS + Playwright), que es la implementación real del navegador.
 * Solo habla con 127.0.0.1 y con token de servicio. Nunca loguea el storageState ni las respuestas.
 */
@Component
class ScraperBrowserAutomation implements BrowserAutomation {

    private static final Logger log = LoggerFactory.getLogger(ScraperBrowserAutomation.class);

    private final RestClient quick;
    private final RestClient longRunning;
    private final JsonMapper json;
    private final LoginStream loginStream;

    ScraperBrowserAutomation(ScraperClients clients, JsonMapper json) {
        this.quick = clients.quick();
        this.longRunning = clients.longRunning();
        this.json = json;
        this.loginStream = new LoginStream(json);
    }

    @Override
    public BrowserState browserState() {
        try {
            JsonNode health = quick.get().uri("/health").retrieve().body(JsonNode.class);
            return BrowserState.valueOf(health.path("browser").asString("FAILED"));
        } catch (RuntimeException e) {
            return BrowserState.UNAVAILABLE;
        }
    }

    @Override
    public LoginResult login(UUID attemptId, Duration timeout, Consumer<ConnectStep> onStep) {
        ObjectNode request = json.createObjectNode()
                .put("attemptId", attemptId.toString())
                .put("timeoutMs", timeout.toMillis());
        try {
            return longRunning.post()
                    .uri("/sessions/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.parseMediaType("application/x-ndjson"))
                    .body(request)
                    .exchange((req, res) -> {
                        if (res.getStatusCode().isError()) {
                            throw LoginStream.error(json.readTree(res.getBody()));
                        }
                        return loginStream.read(res.getBody(), onStep);
                    });
        } catch (ResourceAccessException e) {
            throw ConnectException.scraperUnavailable(e);
        }
    }

    @Override
    public void cancelLogin(UUID attemptId) {
        try {
            quick.delete().uri("/sessions/login/{id}", attemptId).retrieve().toBodilessEntity();
        } catch (RuntimeException e) {
            log.warn("No se pudo cancelar el intento {} en el scraper: {}", attemptId, e.getMessage());
        }
    }

    @Override
    public SessionResult validate(String storageState) {
        JsonNode response = post("/sessions/validate", storageState);
        boolean valid = response.path("valid").asBoolean(false);
        return new SessionResult(valid, valid ? response.get("storageState").toString() : null);
    }

    @Override
    public ProfileResult readProfile(String storageState) {
        JsonNode response = post("/sessions/read-profile", storageState);
        boolean valid = response.path("valid").asBoolean(false);
        if (!valid) {
            return new ProfileResult(false, null, null);
        }
        return new ProfileResult(true, json.treeToValue(response.get("profile"), ProfileSnapshot.class),
                response.get("storageState").toString());
    }

    /** POST /portals/linkedin/jobs/search: recorre varias páginas, por eso usa el cliente de larga duración. */
    @Override
    public JobSearchResult searchJobs(String storageState, JobSearchCriteria criteria) {
        ObjectNode body = json.createObjectNode();
        body.set("storageState", json.readTree(storageState));
        ObjectNode c = body.putObject("criteria")
                .put("keywords", criteria.keywords())
                .put("maxResults", criteria.maxResults());
        if (criteria.location() != null) {
            c.put("location", criteria.location());
        }
        if (criteria.postedWithinDays() != null) {
            c.put("postedWithinDays", criteria.postedWithinDays());
        }
        if (criteria.remote()) {
            c.put("remote", true);
        }
        JsonNode response;
        try {
            response = longRunning.post().uri("/portals/linkedin/jobs/search")
                    .contentType(MediaType.APPLICATION_JSON).body(body)
                    .retrieve().body(JsonNode.class);
        } catch (ResourceAccessException e) {
            throw ConnectException.scraperUnavailable(e);
        } catch (RestClientResponseException e) {
            JsonNode error = parseOrEmpty(e.getResponseBodyAsString());
            throw ConnectException.fromScraper(error.path("code").asString("BROWSER_FAILED"),
                    error.path("message").asString("El scraper respondió " + e.getStatusCode()));
        }
        if (!response.path("valid").asBoolean(false)) {
            return new JobSearchResult(false, List.of(), null);
        }
        List<JobListing> jobs = new ArrayList<>();
        response.path("jobs").forEach(job -> jobs.add(json.treeToValue(job, JobListing.class)));
        return new JobSearchResult(true, jobs, response.get("storageState").toString());
    }

    private JsonNode parseOrEmpty(String body) {
        try {
            return json.readTree(body);
        } catch (RuntimeException e) {
            return json.createObjectNode();
        }
    }

    private JsonNode post(String path, String storageState) {
        ObjectNode body = json.createObjectNode();
        body.set("storageState", json.readTree(storageState));
        try {
            return quick.post().uri(path).contentType(MediaType.APPLICATION_JSON).body(body)
                    .retrieve().body(JsonNode.class);
        } catch (ResourceAccessException e) {
            throw ConnectException.scraperUnavailable(e);
        } catch (RestClientResponseException e) {
            throw ConnectException.fromScraper("BROWSER_FAILED", "El scraper respondió " + e.getStatusCode());
        }
    }
}

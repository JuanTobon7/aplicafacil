package com.aplicafacil.computrabajo.infrastructure.scraper;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.aplicafacil.computrabajo.application.exception.ConnectException;
import com.aplicafacil.computrabajo.application.port.out.PortalBrowser;
import com.aplicafacil.computrabajo.domain.ConnectStep;
import com.aplicafacil.computrabajo.infrastructure.client.ServiceTokenProvider;
import com.aplicafacil.computrabajo.infrastructure.config.ComputrabajoProperties;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Adaptador HTTP hacia el scraper ({@code /portals/computrabajo/sessions/**}). Solo habla con 127.0.0.1 y con token de
 * servicio. Nunca loguea el storageState ni las respuestas.
 */
@Component
class ScraperPortalBrowser implements PortalBrowser {

    private static final String SESSIONS = "/portals/computrabajo/sessions";
    private static final Logger log = LoggerFactory.getLogger(ScraperPortalBrowser.class);

    private final RestClient quick;
    private final RestClient longRunning;
    private final JsonMapper json;
    private final LoginStream loginStream;

    ScraperPortalBrowser(RestClient.Builder builder, ComputrabajoProperties properties, ServiceTokenProvider tokens,
                         JsonMapper json) {
        this.quick = client(builder, properties.scraperUrl(), tokens, Duration.ofSeconds(90));
        // El inicio de sesión dura hasta loginTimeout; se deja un margen
        this.longRunning = client(builder, properties.scraperUrl(), tokens, properties.loginTimeout().plusMinutes(1));
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
                    .uri(SESSIONS + "/login")
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
            quick.delete().uri(SESSIONS + "/login/{id}", attemptId).retrieve().toBodilessEntity();
        } catch (RuntimeException e) {
            log.warn("No se pudo cancelar el intento {} en el scraper: {}", attemptId, e.getMessage());
        }
    }

    @Override
    public Optional<String> validate(String storageState) {
        ObjectNode body = json.createObjectNode();
        body.set("storageState", json.readTree(storageState));
        JsonNode response;
        try {
            response = quick.post().uri(SESSIONS + "/validate").contentType(MediaType.APPLICATION_JSON).body(body)
                    .retrieve().body(JsonNode.class);
        } catch (ResourceAccessException e) {
            throw ConnectException.scraperUnavailable(e);
        } catch (RestClientResponseException e) {
            throw new ConnectException("BROWSER_FAILED", "El scraper respondió " + e.getStatusCode());
        }
        return response.path("valid").asBoolean(false)
                ? Optional.of(response.get("storageState").toString())
                : Optional.empty();
    }

    private static RestClient client(RestClient.Builder builder, String baseUrl, ServiceTokenProvider tokens,
                                     Duration readTimeout) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(readTimeout);
        return builder.clone()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .requestInterceptor(tokens.interceptor())
                .build();
    }
}

package com.aplicafacil.computrabajo.infrastructure.scraper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.web.client.RestClient;

import com.aplicafacil.computrabajo.application.exception.ConnectException;
import com.aplicafacil.computrabajo.application.port.out.PortalBrowser.BrowserState;
import com.aplicafacil.computrabajo.application.port.out.PortalBrowser.LoginResult;
import com.aplicafacil.computrabajo.domain.ConnectStep;
import com.aplicafacil.computrabajo.infrastructure.client.ServiceTokenProvider;
import com.aplicafacil.computrabajo.infrastructure.config.ComputrabajoProperties;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import tools.jackson.databind.json.JsonMapper;

/** El adaptador habla /portals/computrabajo/sessions/** (contracts/openapi/scraper-browser.yaml) con un scraper falso. */
class ScraperPortalBrowserTest {

    private HttpServer server;
    private ScraperPortalBrowser browser;
    private String loginResponse;
    private String validateResponse;
    private String lastAuthorization;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/health", ex -> respond(ex, "{\"browser\":\"READY\"}"));
        server.createContext("/portals/computrabajo/sessions/login", ex -> {
            lastAuthorization = ex.getRequestHeaders().getFirst("Authorization");
            respond(ex, loginResponse);
        });
        server.createContext("/portals/computrabajo/sessions/validate", ex -> respond(ex, validateResponse));
        server.start();

        ServiceTokenProvider tokens = mock(ServiceTokenProvider.class);
        ClientHttpRequestInterceptor bearer = (request, body, execution) -> {
            request.getHeaders().setBearerAuth("service-token");
            return execution.execute(request, body);
        };
        when(tokens.interceptor()).thenReturn(bearer);
        ComputrabajoProperties properties = new ComputrabajoProperties(Duration.ofMinutes(1),
                "http://127.0.0.1:" + server.getAddress().getPort(), null, null);
        browser = new ScraperPortalBrowser(RestClient.builder(), properties, tokens, JsonMapper.builder().build());
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private static void respond(HttpExchange ex, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", ex.getRequestURI().getPath().endsWith("/login")
                ? "application/x-ndjson" : "application/json");
        ex.sendResponseHeaders(200, bytes.length);
        ex.getResponseBody().write(bytes);
        ex.close();
    }

    @Test
    void loginForwardsStepsAndReturnsTheNameAndSession() {
        loginResponse = """
                {"type":"step","step":"WAITING_FOR_LOGIN"}
                {"type":"step","step":"READING_PROFILE"}
                {"type":"result","identity":{"name":"Ana Pérez"},"profile":{},"storageState":{"cookies":[]}}
                """;
        List<ConnectStep> steps = new ArrayList<>();

        LoginResult result = browser.login(UUID.randomUUID(), Duration.ofMinutes(1), steps::add);

        assertThat(steps).containsExactly(ConnectStep.WAITING_FOR_LOGIN, ConnectStep.READING_PROFILE);
        assertThat(result.candidateName()).isEqualTo("Ana Pérez");
        assertThat(result.storageState()).isEqualTo("{\"cookies\":[]}");
        assertThat(result.toString()).doesNotContain("cookies");
        assertThat(lastAuthorization).isEqualTo("Bearer service-token");
        assertThat(browser.browserState()).isEqualTo(BrowserState.READY);
    }

    @Test
    void aScraperErrorKeepsItsCode() {
        loginResponse = "{\"type\":\"error\",\"code\":\"WINDOW_CLOSED\",\"message\":\"Cerraste la ventana\"}\n";

        assertThatThrownBy(() -> browser.login(UUID.randomUUID(), Duration.ofMinutes(1), s -> { }))
                .isInstanceOfSatisfying(ConnectException.class, e -> assertThat(e.code()).isEqualTo("WINDOW_CLOSED"));
    }

    @Test
    void validateReturnsTheRenewedSessionOrNothingIfItExpired() {
        validateResponse = "{\"valid\":true,\"storageState\":{\"cookies\":[1]}}";
        assertThat(browser.validate("{\"cookies\":[]}")).contains("{\"cookies\":[1]}");

        validateResponse = "{\"valid\":false}";
        assertThat(browser.validate("{\"cookies\":[]}")).isEmpty();
    }

    @Test
    void aStoppedScraperIsReportedAsUnavailable() {
        server.stop(0);

        assertThat(browser.browserState()).isEqualTo(BrowserState.UNAVAILABLE);
        assertThatThrownBy(() -> browser.validate("{}"))
                .isInstanceOfSatisfying(ConnectException.class, e -> assertThat(e.code()).isEqualTo("SCRAPER_UNAVAILABLE"));
    }
}

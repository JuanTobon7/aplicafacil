package com.aplicafacil.jobs.infrastructure.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.web.client.RestClient;

import com.aplicafacil.jobs.application.port.out.JobPortalException;
import com.aplicafacil.jobs.domain.JobCard;
import com.aplicafacil.jobs.domain.JobPlatform;
import com.aplicafacil.jobs.domain.SearchCriteria;
import com.aplicafacil.jobs.infrastructure.config.JobsProperties;
import com.sun.net.httpserver.HttpServer;

import tools.jackson.databind.json.JsonMapper;

/**
 * Los dos gateways (Strategy) contra servidores HTTP falsos: LinkedIn pasa por linkedin-service y Computrabajo va
 * directo al scraper; ambos traducen los errores con la misma política de reintento.
 */
class JobPortalGatewaysHttpTest {

    private static final String JOBS = """
            {"valid":true,"pagesVisited":1,"jobs":[{"externalId":"42","url":"https://portal.example/jobs/42",
             "title":"Backend","company":"Acme","location":"Bogotá","postedText":"hoy","salaryText":null,
             "quickApply":true}]}
            """;

    private HttpServer server;
    private int status = 200;
    private String response = JOBS;
    private String lastPath;
    private String lastBody;
    private String lastAuthorization;
    private LinkedInJobPortalGateway linkedin;
    private ComputrabajoJobPortalGateway computrabajo;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", ex -> {
            lastPath = ex.getRequestURI().getPath();
            lastBody = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            lastAuthorization = ex.getRequestHeaders().getFirst("Authorization");
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(status, bytes.length);
            ex.getResponseBody().write(bytes);
            ex.close();
        });
        server.start();
        String url = "http://127.0.0.1:" + server.getAddress().getPort();
        JobsProperties properties = new JobsProperties(url, url, url, "secret", Duration.ofSeconds(10), null);
        ServiceTokenProvider tokens = mock(ServiceTokenProvider.class);
        ClientHttpRequestInterceptor bearer = (request, body, execution) -> {
            request.getHeaders().setBearerAuth("service-token");
            return execution.execute(request, body);
        };
        when(tokens.interceptor()).thenReturn(bearer);
        JsonMapper json = JsonMapper.builder().build();
        linkedin = new LinkedInJobPortalGateway(RestClient.builder(), properties, tokens, json);
        computrabajo = new ComputrabajoJobPortalGateway(RestClient.builder(), properties, tokens, json);
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    void linkedInGoesThroughLinkedinServiceWithTheUser() {
        UUID userId = UUID.randomUUID();
        List<JobCard> cards = linkedin.search(userId, new SearchCriteria("java", "Bogotá", 30, 7, true));

        assertThat(linkedin.platform()).isEqualTo(JobPlatform.LINKEDIN);
        assertThat(lastPath).isEqualTo("/internal/sessions/" + userId + "/job-search");
        assertThat(lastBody).contains("\"keywords\":\"java\"").contains("\"maxResults\":30")
                .contains("\"postedWithinDays\":7").contains("\"remote\":true").doesNotContain("storageState");
        assertThat(lastAuthorization).isEqualTo("Bearer service-token");
        assertThat(cards).singleElement().satisfies(card -> {
            assertThat(card.externalId()).isEqualTo("42");
            assertThat(card.quickApply()).isTrue();
        });
    }

    @Test
    void computrabajoGoesDirectlyToTheScraperWithoutSession() {
        computrabajo.search(UUID.randomUUID(), new SearchCriteria("java", null, 10, null));
        assertThat(computrabajo.platform()).isEqualTo(JobPlatform.COMPUTRABAJO);
        assertThat(lastPath).isEqualTo("/portals/computrabajo/jobs/search");
        assertThat(lastBody).contains("\"criteria\":{").doesNotContain("location").doesNotContain("storageState")
                .doesNotContain("remote");
    }

    @Test
    void errorsKeepTheirCodeAndRetryPolicy() {
        expectError(409, "{\"code\":\"SESSION_BUSY\",\"detail\":\"Hay una automatización usando esta cuenta.\"}",
                "SESSION_BUSY", true);
        expectError(409, "{\"code\":\"SESSION_EXPIRED\",\"detail\":\"Tu sesión de LinkedIn expiró.\"}",
                "SESSION_EXPIRED", false);
        expectError(409, "{\"code\":\"NOT_CONNECTED\"}", "NOT_CONNECTED", false);
        expectError(502, "{\"code\":\"SEARCH_FAILED\",\"message\":\"LinkedIn no mostró el listado\"}",
                "SEARCH_FAILED", true);
        expectError(503, "no-json", "HTTP_503", true);
        expectError(400, "{\"code\":\"INVALID_REQUEST\"}", "INVALID_REQUEST", false);
    }

    @Test
    void anUnreachableServiceIsRetryable() {
        server.stop(0);
        assertThatThrownBy(() -> computrabajo.search(UUID.randomUUID(), new SearchCriteria("java", null, 5, null)))
                .isInstanceOfSatisfying(JobPortalException.class, e -> {
                    assertThat(e.code()).isEqualTo("PORTAL_UNAVAILABLE");
                    assertThat(e.retryable()).isTrue();
                });
    }

    private void expectError(int status, String body, String code, boolean retryable) {
        this.status = status;
        this.response = body;
        assertThatThrownBy(() -> linkedin.search(UUID.randomUUID(), new SearchCriteria("java", null, 5, null)))
                .as(code)
                .isInstanceOfSatisfying(JobPortalException.class, e -> {
                    assertThat(e.code()).isEqualTo(code);
                    assertThat(e.retryable()).isEqualTo(retryable);
                });
    }
}

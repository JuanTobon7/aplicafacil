package com.aplicafacil.linkedin.infrastructure.scraper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.OutputStream;
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

import com.aplicafacil.linkedin.application.exception.ConnectException;
import com.aplicafacil.linkedin.application.port.out.BrowserAutomation.BrowserState;
import com.aplicafacil.linkedin.application.port.out.BrowserAutomation.JobSearchResult;
import com.aplicafacil.linkedin.domain.JobSearchCriteria;
import com.aplicafacil.linkedin.application.port.out.BrowserAutomation.LoginResult;
import com.aplicafacil.linkedin.application.port.out.BrowserAutomation.SessionResult;
import com.aplicafacil.linkedin.domain.ConnectStep;
import com.aplicafacil.linkedin.infrastructure.client.ServiceTokenProvider;
import com.aplicafacil.linkedin.infrastructure.config.LinkedInProperties;
import com.sun.net.httpserver.HttpServer;

import tools.jackson.databind.json.JsonMapper;

/** El proxy habla el contrato de contracts/openapi/scraper-browser.yaml contra un scraper falso. */
class ScraperBrowserAutomationTest {

    private HttpServer server;
    private ScraperBrowserAutomation automation;
    private String loginResponse;
    private String validateResponse;
    private String lastAuthorization;
    private String searchResponse;
    private int searchStatus;
    private String lastSearchBody;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/health", ex -> respond(ex.getResponseBody(), ex, "{\"browser\":\"READY\"}"));
        server.createContext("/sessions/login", ex -> {
            lastAuthorization = ex.getRequestHeaders().getFirst("Authorization");
            respond(ex.getResponseBody(), ex, loginResponse);
        });
        server.createContext("/sessions/validate", ex -> respond(ex.getResponseBody(), ex, validateResponse));
        server.createContext("/portals/linkedin/jobs/search", ex -> {
            lastSearchBody = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            byte[] bytes = searchResponse.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(searchStatus, bytes.length);
            ex.getResponseBody().write(bytes);
            ex.close();
        });
        server.start();

        ServiceTokenProvider tokens = mock(ServiceTokenProvider.class);
        ClientHttpRequestInterceptor bearer = (request, body, execution) -> {
            request.getHeaders().setBearerAuth("service-token");
            return execution.execute(request, body);
        };
        when(tokens.interceptor()).thenReturn(bearer);
        LinkedInProperties properties = new LinkedInProperties(null, null, Duration.ofMinutes(5), null, null, null,
                null, "http://127.0.0.1:" + server.getAddress().getPort());
        automation = new ScraperBrowserAutomation(new ScraperClients(RestClient.builder(), properties, tokens),
                JsonMapper.builder().build());
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    void streamsStepsAndReturnsTheLoginResult() {
        loginResponse = """
                {"type":"step","step":"OPENING_BROWSER"}
                {"type":"step","step":"WAITING_FOR_LOGIN"}
                {"type":"step","step":"READING_PROFILE"}
                %s
                """.formatted("{\"type\":\"result\",\"identity\":{\"source\":\"SESSION\",\"memberUrn\":\"urn:li:fsd_profile:ANA\","
                + "\"emailVerified\":false,\"basics\":{\"firstName\":\"Ana\"}},"
                + "\"profile\":{\"complete\":false,\"missingSections\":[\"skills\"]},"
                + "\"storageState\":{\"cookies\":[{\"name\":\"li_at\",\"value\":\"x\"}],\"origins\":[]}}");
        List<ConnectStep> steps = new ArrayList<>();

        LoginResult result = automation.login(UUID.randomUUID(), Duration.ofMinutes(5), steps::add);

        assertThat(steps).containsExactly(ConnectStep.OPENING_BROWSER, ConnectStep.WAITING_FOR_LOGIN,
                ConnectStep.READING_PROFILE);
        assertThat(result.identity().memberUrn()).isEqualTo("urn:li:fsd_profile:ANA");
        assertThat(result.profile().missingSections()).containsExactly("skills");
        assertThat(result.storageState()).contains("\"li_at\"");
        assertThat(result.toString()).doesNotContain("li_at");
        assertThat(lastAuthorization).isEqualTo("Bearer service-token");
    }

    @Test
    void translatesScraperErrors() {
        loginResponse = """
                {"type":"step","step":"WAITING_FOR_LOGIN"}
                {"type":"error","code":"WINDOW_CLOSED","message":"Cerraste la ventana"}
                """;

        assertThatThrownBy(() -> automation.login(UUID.randomUUID(), Duration.ofMinutes(5), s -> { }))
                .isInstanceOfSatisfying(ConnectException.class, e -> assertThat(e.code()).isEqualTo("WINDOW_CLOSED"));
    }

    @Test
    void validateReturnsTheRenewedState() {
        validateResponse = "{\"valid\":true,\"storageState\":{\"cookies\":[],\"origins\":[]}}";
        SessionResult valid = automation.validate("{\"cookies\":[],\"origins\":[]}");
        assertThat(valid.valid()).isTrue();
        assertThat(valid.storageState()).contains("cookies");

        validateResponse = "{\"valid\":false}";
        assertThat(automation.validate("{}").valid()).isFalse();
    }

    @Test
    void searchJobsSendsCriteriaAndSessionAndReadsTheCards() {
        searchStatus = 200;
        searchResponse = """
                {"valid":true,"pagesVisited":1,"storageState":{"cookies":[{"name":"li_at","value":"new"}],"origins":[]},
                 "jobs":[{"externalId":"4012345678","url":"https://www.linkedin.com/jobs/view/4012345678/",
                          "title":"Backend Developer","company":"Acme","location":"Bogotá","postedText":"2026-10-14",
                          "salaryText":null,"quickApply":true}]}
                """;

        JobSearchResult result = automation.searchJobs("{\"cookies\":[{\"name\":\"li_at\",\"value\":\"old\"}],\"origins\":[]}",
                new JobSearchCriteria("java", "Bogotá", 25, 7, true));

        assertThat(result.valid()).isTrue();
        assertThat(result.jobs()).singleElement().satisfies(job -> {
            assertThat(job.externalId()).isEqualTo("4012345678");
            assertThat(job.quickApply()).isTrue();
        });
        assertThat(result.storageState()).contains("new");
        assertThat(result.toString()).doesNotContain("li_at");
        assertThat(lastSearchBody).contains("\"keywords\":\"java\"").contains("\"postedWithinDays\":7").contains("\"remote\":true").contains("old");
    }

    @Test
    void searchJobsReportsExpiredSessionsAndScraperErrors() {
        searchStatus = 200;
        searchResponse = "{\"valid\":false,\"jobs\":[],\"pagesVisited\":0}";
        assertThat(automation.searchJobs("{}", new JobSearchCriteria("java", null, 10, null)).valid()).isFalse();

        searchStatus = 502;
        searchResponse = "{\"code\":\"SEARCH_FAILED\",\"message\":\"LinkedIn no mostró el listado\"}";
        assertThatThrownBy(() -> automation.searchJobs("{}", new JobSearchCriteria("java", null, 10, null)))
                .isInstanceOfSatisfying(ConnectException.class, e -> assertThat(e.code()).isEqualTo("SEARCH_FAILED"));
    }

    @Test
    void reportsHealthAndUnavailableScraper() {
        assertThat(automation.browserState()).isEqualTo(BrowserState.READY);
        server.stop(0);
        assertThat(automation.browserState()).isEqualTo(BrowserState.UNAVAILABLE);
        assertThatThrownBy(() -> automation.login(UUID.randomUUID(), Duration.ofMinutes(1), s -> { }))
                .isInstanceOfSatisfying(ConnectException.class,
                        e -> assertThat(e.code()).isEqualTo("SCRAPER_UNAVAILABLE"));
    }

    private static void respond(OutputStream out, com.sun.net.httpserver.HttpExchange ex, String body)
            throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json");
        ex.sendResponseHeaders(200, bytes.length);
        out.write(bytes);
        out.close();
    }
}

package com.aplicafacil.identity.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import com.aplicafacil.identity.application.port.out.LinkedInSessionClient;
import com.aplicafacil.identity.application.port.out.LinkedInSessionClient.SessionCheck;
import com.jayway.jsonpath.JsonPath;

import jakarta.servlet.http.Cookie;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * De punta a punta dentro de identity-service: token de servicio → provisión → canje del código →
 * refresh rotativo → "Continuar como…" → borrado. linkedin-service se simula.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class AuthFlowIntegrationTest {

    private static final String SECRET = "test-service-secret";

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:0.8.6-pg18").asCompatibleSubstituteFor("postgres"));

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        registry.add("aplicafacil.session-encryption.key", () -> Base64.getEncoder().encodeToString(key));
        registry.add("aplicafacil.identity.service-client-secret", () -> SECRET);
    }

    @Autowired
    MockMvc mvc;

    @MockitoBean
    LinkedInSessionClient linkedin;

    @Test
    void fullSessionLifecycle() throws Exception {
        // 1. linkedin-service obtiene un token de servicio
        String serviceToken = JsonPath.read(mvc.perform(post("/internal/token")
                        .header(HttpHeaders.AUTHORIZATION, basic("linkedin-service", SECRET)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "$.accessToken");

        // 2. Provisión del usuario al terminar la captura de sesión
        UUID attemptId = UUID.randomUUID();
        MvcResult provisioned = mvc.perform(post("/internal/users/provision")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + serviceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"attemptId":"%s","identity":{"source":"SESSION","memberUrn":"urn:li:fsd_profile:ANA",
                                 "emailVerified":false,"basics":{"firstName":"Ana","lastName":"Pérez","headline":"Backend"}}}
                                """.formatted(attemptId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("CREATED"))
                .andReturn();
        String body = provisioned.getResponse().getContentAsString();
        String userId = JsonPath.read(body, "$.userId");
        String loginCode = JsonPath.read(body, "$.loginCode");

        // 3. El front canjea el código: access token en el cuerpo, refresh en cookie
        MvcResult session = mvc.perform(post("/auth/session").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"attemptId\":\"%s\",\"loginCode\":\"%s\"}".formatted(attemptId, loginCode)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.firstName").value("Ana"))
                .andReturn();
        String setCookie = session.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).contains("af_rt=", "HttpOnly", "Secure", "SameSite=Strict", "Path=/api/auth");
        String accessToken = JsonPath.read(session.getResponse().getContentAsString(), "$.accessToken");
        Cookie refresh = session.getResponse().getCookie("af_rt");

        // El código es de un solo uso
        mvc.perform(post("/auth/session").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"attemptId\":\"%s\",\"loginCode\":\"%s\"}".formatted(attemptId, loginCode)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_LOGIN_CODE"));

        // 4. El access token sirve para /auth/me; un token de servicio no
        mvc.perform(get("/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userId));
        mvc.perform(get("/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + serviceToken))
                .andExpect(status().isForbidden());

        // 5. Refresh rotativo: reusar el token anterior revoca la familia
        MvcResult refreshed = mvc.perform(post("/auth/refresh").cookie(refresh))
                .andExpect(status().isOk()).andReturn();
        Cookie rotated = refreshed.getResponse().getCookie("af_rt");
        assertThat(rotated.getValue()).isNotEqualTo(refresh.getValue());
        mvc.perform(post("/auth/refresh").cookie(refresh)).andExpect(status().isUnauthorized());
        mvc.perform(post("/auth/refresh").cookie(rotated)).andExpect(status().isUnauthorized());

        // 6. "Continuar como…" valida la sesión guardada en linkedin-service
        when(linkedin.connectionStatuses(any())).thenReturn(Map.of(UUID.fromString(userId), "CONNECTED"));
        mvc.perform(get("/auth/accounts"))
                .andExpect(jsonPath("$[0].displayName").value("Ana Pérez"))
                .andExpect(jsonPath("$[0].connectionStatus").value("CONNECTED"));
        when(linkedin.validate(UUID.fromString(userId))).thenReturn(SessionCheck.EXPIRED);
        mvc.perform(post("/auth/accounts/{id}/session", userId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SESSION_EXPIRED"));
        when(linkedin.validate(UUID.fromString(userId))).thenReturn(SessionCheck.VALID);
        mvc.perform(post("/auth/accounts/{id}/session", userId)).andExpect(status().isOk());

        // 7. JWKS público
        mvc.perform(get("/.well-known/jwks.json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keys[0].kty").value("RSA"))
                .andExpect(jsonPath("$.keys[0].d").doesNotExist());

        // 8. Eliminar la cuenta borra primero la sesión de LinkedIn
        mvc.perform(delete("/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isNoContent());
        verify(linkedin).deleteSession(UUID.fromString(userId));
        mvc.perform(get("/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsWrongServiceCredentials() throws Exception {
        mvc.perform(post("/internal/token").header(HttpHeaders.AUTHORIZATION, basic("linkedin-service", "otro")))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/internal/token").header(HttpHeaders.AUTHORIZATION, basic("intruso", SECRET)))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/internal/users/provision").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    private static String basic(String user, String password) {
        return "Basic " + Base64.getEncoder().encodeToString((user + ":" + password).getBytes(StandardCharsets.UTF_8));
    }
}

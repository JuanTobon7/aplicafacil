package com.aplicafacil.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.AnonymousQueue;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.util.UriComponentsBuilder;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

import com.aplicafacil.auth.application.RegisterUserUseCase;
import com.aplicafacil.auth.application.port.UserRepository;
import com.aplicafacil.auth.domain.User;
import com.jayway.jsonpath.JsonPath;

/**
 * Flujos OAuth 2.1 / OIDC completos contra Postgres y RabbitMQ reales:
 * discovery, client_credentials, authorization_code + PKCE, token exchange,
 * registro, bloqueo por intentos fallidos y administración.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class OAuth2FlowsIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:0.8.6-pg18").asCompatibleSubstituteFor("postgres"));

    @Container
    @ServiceConnection
    static RabbitMQContainer rabbitmq = new RabbitMQContainer(DockerImageName.parse("rabbitmq:4.3-management"));

    static final String ISSUER = "http://localhost:9000";
    static final String WEB_CLIENT = "aplicafacil-web";
    static final String REDIRECT_URI = "http://localhost:5173/callback";
    static final String PASSWORD = "Segura123";

    @Autowired
    MockMvc mvc;

    @Autowired
    JwtDecoder jwtDecoder;

    @Autowired
    RegisterUserUseCase registerUser;

    @Autowired
    UserRepository users;

    @Autowired
    RabbitTemplate rabbitTemplate;

    @Autowired
    AmqpAdmin amqpAdmin;

    // ── Discovery ───────────────────────────────────────────────────────
    @Test
    void publishesOpenIdConfigurationAndJwks() throws Exception {
        mvc.perform(get("/.well-known/openid-configuration"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.issuer").value(ISSUER))
                .andExpect(jsonPath("$.token_endpoint").value(ISSUER + "/oauth2/token"))
                .andExpect(jsonPath("$.grant_types_supported", hasItem("urn:ietf:params:oauth:grant-type:token-exchange")))
                .andExpect(jsonPath("$.code_challenge_methods_supported", hasItem("S256")));

        mvc.perform(get("/oauth2/jwks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keys[0].kty").value("RSA"))
                .andExpect(jsonPath("$.keys[0].d").doesNotExist()); // nunca la parte privada
    }

    // ── client_credentials (automation-service) ─────────────────────────
    @Test
    void serviceClientGetsAServiceToken() throws Exception {
        String body = mvc.perform(post("/oauth2/token")
                        .with(httpBasic("automation-service", "automation-test-secret"))
                        .param("grant_type", "client_credentials")
                        .param("scope", "people:read jobs:read"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        Jwt token = jwtDecoder.decode(JsonPath.read(body, "$.access_token"));
        assertThat(token.getSubject()).isEqualTo("automation-service");
        assertThat(token.getAudience()).containsExactly("aplicafacil-api");
        assertThat(token.getClaimAsStringList("roles")).containsExactly("SERVICE");
        assertThat(token.getClaimAsStringList("scope")).containsExactlyInAnyOrder("people:read", "jobs:read");
    }

    @Test
    void rejectsAWrongClientSecret() throws Exception {
        mvc.perform(post("/oauth2/token")
                        .with(httpBasic("automation-service", "otro"))
                        .param("grant_type", "client_credentials"))
                .andExpect(status().isUnauthorized());
    }

    // ── Registro ────────────────────────────────────────────────────────
    @Test
    void registrationCreatesTheUserAndPublishesUserRegistered() throws Exception {
        Queue probe = bindProbeQueue("user.registered");
        String email = uniqueEmail();

        mvc.perform(post("/register").with(csrf())
                        .param("firstName", "Juan")
                        .param("lastName", "Tobón")
                        .param("email", email)
                        .param("password", PASSWORD)
                        .param("confirmPassword", PASSWORD))
                .andExpect(redirectedUrl("/login?registered"));

        User user = users.findByEmail(email).orElseThrow();
        Message message = rabbitTemplate.receive(probe.getName(), 10_000);
        assertThat(message).isNotNull();
        String json = new String(message.getBody(), StandardCharsets.UTF_8);
        assertThat((String) JsonPath.read(json, "$.userId")).isEqualTo(user.getId().toString());
        assertThat((String) JsonPath.read(json, "$.email")).isEqualTo(email);
        assertThat((String) JsonPath.read(json, "$.firstName")).isEqualTo("Juan");
    }

    @Test
    void registrationShowsErrorsWithoutEchoingThePassword() throws Exception {
        String email = uniqueEmail();
        register(email);

        mvc.perform(post("/register").with(csrf())
                        .param("firstName", "Juan").param("lastName", "Tobón").param("email", email)
                        .param("password", PASSWORD).param("confirmPassword", PASSWORD))
                .andExpect(status().isOk())
                .andExpect(content()
                        .string(containsString("Ya existe una cuenta con ese email")));

        String page = mvc.perform(post("/register").with(csrf())
                        .param("firstName", "Ana").param("lastName", "Gómez").param("email", uniqueEmail())
                        .param("password", "solamenteletras").param("confirmPassword", "solamenteletras"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(page).contains("letras y números").doesNotContain("solamenteletras");
    }

    // ── Authorization Code + PKCE (web) ─────────────────────────────────
    @Test
    void authorizationCodeWithPkceIssuesUserTokens() throws Exception {
        String email = uniqueEmail();
        User user = register(email);

        String tokenResponse = authorizationCodeFlow(email, PASSWORD);

        Jwt accessToken = jwtDecoder.decode(JsonPath.read(tokenResponse, "$.access_token"));
        assertThat(accessToken.getSubject()).isEqualTo(user.getId().toString());
        assertThat(accessToken.getIssuer().toString()).isEqualTo(ISSUER);
        assertThat(accessToken.getAudience()).containsExactly("aplicafacil-api");
        assertThat(accessToken.getClaimAsStringList("roles")).containsExactly("USER");
        assertThat(accessToken.getClaimAsString("email")).isEqualTo(email);
        assertThat((String) JsonPath.read(tokenResponse, "$.id_token")).isNotBlank();
        // cliente público: sin refresh token
        assertThat(tokenResponse).doesNotContain("refresh_token");

        String bearer = "Bearer " + accessToken.getTokenValue();
        mvc.perform(get("/me").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(user.getId().toString()))
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.lastLoginAt").isNotEmpty())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
        mvc.perform(get("/userinfo").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sub").value(user.getId().toString()));
        mvc.perform(get("/users").header("Authorization", bearer))
                .andExpect(status().isForbidden());
    }

    @Test
    void authorizationRequiresPkce() throws Exception {
        MockHttpSession session = login(registerAndReturnEmail(), PASSWORD);

        MvcResult result = mvc.perform(get("/oauth2/authorize").session(session)
                        .queryParam("response_type", "code")
                        .queryParam("client_id", WEB_CLIENT)
                        .queryParam("redirect_uri", REDIRECT_URI)
                        .queryParam("scope", "openid")
                        .queryParam("state", "st"))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        assertThat(result.getResponse().getRedirectedUrl()).contains("error=invalid_request");
    }

    @Test
    void meRejectsMissingOrForeignTokens() throws Exception {
        mvc.perform(get("/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/me").header("Authorization", "Bearer no.es.jwt")).andExpect(status().isUnauthorized());
    }

    @Test
    void userChangesPasswordWithTheCurrentOne() throws Exception {
        String email = uniqueEmail();
        register(email);
        String bearer = "Bearer " + JsonPath.read(authorizationCodeFlow(email, PASSWORD), "$.access_token");

        mvc.perform(post("/me/password").header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"incorrecta1\",\"newPassword\":\"NuevaClave123\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/me/password").header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"" + PASSWORD + "\",\"newPassword\":\"NuevaClave123\"}"))
                .andExpect(status().isNoContent());

        assertThat(login(email, "NuevaClave123")).isNotNull();
    }

    // ── Token exchange (mcp-gateway, RFC 8693) ──────────────────────────
    @Test
    void mcpGatewayExchangesAUserTokenInsteadOfForwardingIt() throws Exception {
        String email = uniqueEmail();
        User user = register(email);
        String userToken = JsonPath.read(authorizationCodeFlow(email, PASSWORD), "$.access_token");

        String body = mvc.perform(post("/oauth2/token")
                        .with(httpBasic("mcp-gateway", "mcp-test-secret"))
                        .param("grant_type", "urn:ietf:params:oauth:grant-type:token-exchange")
                        .param("subject_token", userToken)
                        .param("subject_token_type", "urn:ietf:params:oauth:token-type:access_token")
                        .param("scope", "profile:read"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        Jwt exchanged = jwtDecoder.decode(JsonPath.read(body, "$.access_token"));
        assertThat(exchanged.getSubject()).isEqualTo(user.getId().toString());
        assertThat(exchanged.getAudience()).containsExactly("aplicafacil-api");
        assertThat(exchanged.getClaimAsStringList("roles")).containsExactly("USER");
        assertThat(exchanged.getClaimAsStringList("scope")).containsExactly("profile:read");
        assertThat(exchanged.getTokenValue()).isNotEqualTo(userToken);
    }

    // ── Fuerza bruta ────────────────────────────────────────────────────
    @Test
    void locksTheAccountAfterRepeatedFailures() throws Exception {
        String email = uniqueEmail();
        register(email);

        for (int i = 0; i < User.MAX_FAILED_ATTEMPTS; i++) {
            mvc.perform(post("/login").with(csrf()).param("username", email).param("password", "Incorrecta1"))
                    .andExpect(redirectedUrl("/login?error"));
        }
        // ni la contraseña correcta entra mientras dure el bloqueo
        MvcResult locked = mvc.perform(post("/login").with(csrf()).param("username", email).param("password", PASSWORD))
                .andExpect(redirectedUrl("/login?error"))
                .andReturn();

        assertThat(users.findByEmail(email).orElseThrow().isLocked(Instant.now())).isTrue();
        mvc.perform(get("/login").param("error", "").session((MockHttpSession) locked.getRequest().getSession()))
                .andExpect(content()
                        .string(containsString("bloqueada")));
    }

    // ── Administración ──────────────────────────────────────────────────
    @Test
    void adminListsAndDisablesUsers() throws Exception {
        String email = uniqueEmail();
        User user = register(email);
        String adminBearer = "Bearer " + JsonPath.read(authorizationCodeFlow("admin@aplicafacil.test", "Admin12345"), "$.access_token");

        mvc.perform(get("/users").header("Authorization", adminBearer).param("size", "500"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(100))
                .andExpect(jsonPath("$.items[*].email", hasItem(email)));

        mvc.perform(patch("/users/{id}/enabled", user.getId()).header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));

        mvc.perform(post("/login").with(csrf()).param("username", email).param("password", PASSWORD))
                .andExpect(redirectedUrl("/login?error"));
    }

    // ── helpers ─────────────────────────────────────────────────────────
    private String authorizationCodeFlow(String email, String password) throws Exception {
        MockHttpSession session = login(email, password);
        String verifier = codeVerifier();

        MvcResult authorize = mvc.perform(get("/oauth2/authorize").session(session)
                        .queryParam("response_type", "code")
                        .queryParam("client_id", WEB_CLIENT)
                        .queryParam("redirect_uri", REDIRECT_URI)
                        .queryParam("scope", "openid profile email")
                        .queryParam("state", "st-123")
                        .queryParam("code_challenge", codeChallenge(verifier))
                        .queryParam("code_challenge_method", "S256"))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        String location = authorize.getResponse().getRedirectedUrl();
        assertThat(location).startsWith(REDIRECT_URI + "?code=").contains("state=st-123");
        String code = UriComponentsBuilder.fromUriString(location).build().getQueryParams().getFirst("code");

        return mvc.perform(post("/oauth2/token")
                        .param("grant_type", "authorization_code")
                        .param("code", code)
                        .param("redirect_uri", REDIRECT_URI)
                        .param("client_id", WEB_CLIENT)
                        .param("code_verifier", verifier))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private MockHttpSession login(String email, String password) throws Exception {
        MvcResult result = mvc.perform(post("/login").with(csrf()).param("username", email).param("password", password))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        assertThat(result.getResponse().getRedirectedUrl()).doesNotContain("error");
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private User register(String email) {
        return registerUser.register(RegisterUserUseCase.Command.withUserRole(email, PASSWORD, "Test", "User"));
    }

    private String registerAndReturnEmail() {
        String email = uniqueEmail();
        register(email);
        return email;
    }

    private Queue bindProbeQueue(String routingKey) {
        TopicExchange events = new TopicExchange("aplicafacil.events");
        Queue queue = new AnonymousQueue();
        amqpAdmin.declareExchange(events);
        amqpAdmin.declareQueue(queue);
        amqpAdmin.declareBinding(BindingBuilder.bind(queue).to(events).with(routingKey));
        return queue;
    }

    private static String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@aplicafacil.test";
    }

    private static String codeVerifier() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String codeChallenge(String verifier) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
    }
}

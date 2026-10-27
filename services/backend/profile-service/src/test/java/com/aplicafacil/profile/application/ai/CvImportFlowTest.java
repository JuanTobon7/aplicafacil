package com.aplicafacil.profile.application.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import com.aplicafacil.profile.application.exception.ProfileRejectedException;
import com.aplicafacil.profile.application.port.out.CvTextExtractor;
import com.aplicafacil.profile.application.port.out.CvTextExtractor.CvFile;
import com.aplicafacil.profile.domain.AiTask;
import com.aplicafacil.profile.domain.AiTaskKind;
import com.aplicafacil.profile.domain.AiTaskStatus;
import com.aplicafacil.profile.infrastructure.ai.JacksonAiOutputReader;
import com.aplicafacil.profile.infrastructure.messaging.OutboxAiRequests;
import com.aplicafacil.profile.infrastructure.persistence.JpaAiTaskRepository;
import com.aplicafacil.profile.infrastructure.persistence.JpaUserProfileRepository;
import com.aplicafacil.profile.infrastructure.persistence.entity.OutboxEventEntity;
import com.aplicafacil.profile.infrastructure.persistence.entity.SkillEntity;
import com.aplicafacil.profile.infrastructure.persistence.entity.UserProfileEntity;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * CV → {@code ai.request.submit} en el outbox → resultado de la IA → perfil con sus entidades → búsqueda sugerida,
 * contra un Postgres real. El mcp-gateway se simula entregando el resultado a {@link AiResultService}.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({CvImportService.class, SearchPreferenceService.class, AiResultService.class, AiTaskSubmitter.class,
        CvExtractionCompletion.class, SearchSuggestionCompletion.class, JpaUserProfileRepository.class,
        JpaAiTaskRepository.class, OutboxAiRequests.class, JacksonAiOutputReader.class, CvImportFlowTest.Config.class})
@Testcontainers
class CvImportFlowTest {

    private static final String CV_TEXT = "Juan Pérez — Desarrollador Backend. ".repeat(10);
    private static final String EXTRACTED = """
            {"headline": "Desarrollador Backend Java", "summary": "Backend con Spring.",
             "skills": [{"name": "Java", "category": "LANGUAGE", "level": "ADVANCED", "yearsOfExperience": 4},
                        {"name": "Spring Boot", "category": "FRAMEWORK"}, {"name": "java"}],
             "experiences": [{"title": "Backend Developer", "company": "ACME", "startDate": "2021-03",
                              "endDate": null, "technologies": ["Spring Boot"]}],
             "education": [], "languages": [{"name": "Inglés", "proficiency": "B2"}], "certifications": []}
            """;

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:0.8.6-pg18").asCompatibleSubstituteFor("postgres"))
            .withInitScript("init-pgvector.sql");

    @Autowired
    CvImportService cvs;

    @Autowired
    AiResultService results;

    @Autowired
    TestEntityManager em;

    @Autowired
    JsonMapper json;

    private final UUID userId = UUID.randomUUID();
    private final UUID configId = UUID.randomUUID();

    @Test
    void aCvFillsTheProfileAndThenTheAiChoosesTheSearch() {
        AiTask extraction = cvs.importCv(userId, configId, cv("cv.pdf"));

        JsonNode command = lastCommand();
        assertThat(command.path("requestId").asString()).isEqualTo(extraction.id().toString());
        assertThat(command.path("configId").asString()).isEqualTo(configId.toString());
        assertThat(command.path("messages").get(1).path("content").asString()).contains("Desarrollador Backend");

        assertThat(results.apply(completed(extraction.id(), EXTRACTED))).isTrue();
        UserProfileEntity profile = reload();
        assertThat(profile.getHeadline()).isEqualTo("Desarrollador Backend Java");
        assertThat(profile.getSkills()).extracting(SkillEntity::getName).containsExactly("Java", "Spring Boot");
        assertThat(profile.getExperiences()).singleElement()
                .satisfies(e -> assertThat(e.getTechnologies()).containsExactly("Spring Boot"));
        assertThat(profile.getCvImportedAt()).isNotNull();

        // Con el perfil armado, se pidió la búsqueda al mismo proveedor
        JsonNode suggestionCommand = lastCommand();
        UUID suggestionId = UUID.fromString(suggestionCommand.path("requestId").asString());
        assertThat(suggestionId).isNotEqualTo(extraction.id());
        assertThat(suggestionCommand.path("messages").get(1).path("content").asString()).contains("Java (4 años)");

        results.apply(completed(suggestionId, """
                {"keywords": "desarrollador backend java spring boot", "alternatives": ["desarrollador java"],
                 "rationale": "Tu experiencia es backend con Spring."}"""));
        assertThat(reload().getSearch().getKeywords()).isEqualTo("desarrollador backend java spring boot");

        // Un resultado repetido no hace nada
        assertThat(results.apply(completed(extraction.id(), EXTRACTED))).isFalse();
    }

    @Test
    void anUnreadableAnswerFailsTheTaskWithoutTouchingTheProfile() {
        AiTask extraction = cvs.importCv(userId, configId, cv("cv.docx"));

        results.apply(completed(extraction.id(), "Lo siento, no puedo."));

        em.flush();
        em.clear();
        assertThat(em.find(UserProfileEntity.class, userId)).isNull();
        var task = em.find(com.aplicafacil.profile.infrastructure.persistence.entity.AiTaskEntity.class,
                extraction.id());
        assertThat(task.getStatus()).isEqualTo(AiTaskStatus.FAILED);
        assertThat(task.getErrorCode()).isEqualTo("INVALID_OUTPUT");
    }

    @Test
    void rejectsWhatIsNotACvAndASecondTaskWhileOneIsPending() {
        assertThatThrownBy(() -> cvs.importCv(userId, configId, cv("foto.png")))
                .isInstanceOf(ProfileRejectedException.class).hasMessageContaining("PDF");
        assertThatThrownBy(() -> cvs.importCv(userId, configId, new CvFile("cv.pdf", null, "corto".getBytes())))
                .isInstanceOf(ProfileRejectedException.class).hasMessageContaining("texto");

        cvs.importCv(userId, configId, cv("cv.pdf"));
        assertThatThrownBy(() -> cvs.importCv(userId, configId, cv("cv.pdf")))
                .isInstanceOf(ProfileRejectedException.class).hasMessageContaining("en proceso");
    }

    @Test
    void resultsOfOtherServicesOrOtherUsersAreIgnored() {
        AiTask extraction = cvs.importCv(userId, configId, cv("cv.pdf"));

        assertThat(results.apply(completed(UUID.randomUUID(), EXTRACTED))).isFalse();
        assertThat(results.apply(new AiResultService.AiResult(extraction.id(), UUID.randomUUID(), configId, true,
                EXTRACTED, null, null))).isFalse();
        assertThat(extraction.kind()).isEqualTo(AiTaskKind.CV_EXTRACTION);
    }

    private AiResultService.AiResult completed(UUID requestId, String text) {
        return new AiResultService.AiResult(requestId, userId, configId, true, text, null, null);
    }

    private static CvFile cv(String name) {
        return new CvFile(name, "application/pdf", CV_TEXT.getBytes(StandardCharsets.UTF_8));
    }

    private JsonNode lastCommand() {
        em.flush();
        List<OutboxEventEntity> events = em.getEntityManager()
                .createQuery("select e from OutboxEventEntity e order by e.createdAt desc, e.id", OutboxEventEntity.class)
                .getResultList();
        OutboxEventEntity last = events.stream()
                .filter(e -> e.getEventType().equals("ai.request.submit"))
                .reduce((a, b) -> a.getCreatedAt().isAfter(b.getCreatedAt()) ? a : b).orElseThrow();
        return json.readTree(last.getPayload());
    }

    private UserProfileEntity reload() {
        em.flush();
        em.clear();
        return em.find(UserProfileEntity.class, userId);
    }

    @TestConfiguration
    static class Config {

        @Bean
        Clock clock() {
            return Clock.systemUTC();
        }

        @Bean
        JsonMapper jsonMapper() {
            return JsonMapper.builder().build();
        }

        @Bean
        ProfileAiPolicy profileAiPolicy() {
            return new ProfileAiPolicy(null, 0, 0);
        }

        /** El texto del "archivo" es el CV: así la prueba no depende de Tika. */
        @Bean
        CvTextExtractor cvTextExtractor() {
            return file -> new String(file.content(), StandardCharsets.UTF_8);
        }
    }
}

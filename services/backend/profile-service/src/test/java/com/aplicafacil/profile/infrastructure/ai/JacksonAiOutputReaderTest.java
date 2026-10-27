package com.aplicafacil.profile.infrastructure.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.YearMonth;

import org.junit.jupiter.api.Test;

import com.aplicafacil.profile.application.port.out.AiOutputReader.InvalidAiOutputException;
import com.aplicafacil.profile.domain.Experience;
import com.aplicafacil.profile.domain.ProfileContent;
import com.aplicafacil.profile.domain.Seniority;
import com.aplicafacil.profile.domain.Skill;
import com.aplicafacil.profile.domain.SkillCategory;
import com.aplicafacil.profile.domain.SkillLevel;
import com.aplicafacil.profile.domain.WorkProfile;
import com.aplicafacil.profile.domain.WorkRole;

import tools.jackson.databind.json.JsonMapper;

/** Lo que devuelven los modelos de verdad: JSON envuelto en texto, fechas a medias, entradas incompletas. */
class JacksonAiOutputReaderTest {

    private final JacksonAiOutputReader reader = new JacksonAiOutputReader(JsonMapper.builder().build());

    @Test
    void readsACvWrappedInACodeBlock() {
        String output = """
                Aquí está el perfil:
                ```json
                {
                  "headline": "Desarrollador Backend Java",
                  "summary": "Backend con 4 años en Java.",
                  "skills": [
                    {"name": "Java", "category": "language", "level": "ADVANCED", "yearsOfExperience": 4},
                    {"name": "Spring Boot", "category": "FRAMEWORK", "level": null, "yearsOfExperience": null},
                    {"name": "", "category": "TOOL"},
                    {"name": "Docker", "category": "CONTAINERS", "level": "pro"}
                  ],
                  "experiences": [
                    {"title": "Backend Developer", "company": "ACME", "startDate": "2021-03", "endDate": null,
                     "description": "APIs REST", "technologies": ["Spring Boot", "PostgreSQL", 3]},
                    {"title": "Practicante", "company": null, "startDate": "2020"}
                  ],
                  "education": [{"institution": "Universidad de Antioquia", "degree": "Ingeniería de Sistemas",
                                 "startDate": "2016", "endDate": "12/2021"}],
                  "languages": [{"name": "Inglés", "proficiency": "B2"}],
                  "certifications": [{"name": "AWS Cloud Practitioner", "issuer": "AWS", "issuedOn": "2023-07"}]
                }
                ```
                """;

        ProfileContent profile = reader.readProfile(output);

        assertThat(profile.headline()).isEqualTo("Desarrollador Backend Java");
        assertThat(profile.skills()).extracting(Skill::name).containsExactly("Java", "Spring Boot", "Docker");
        assertThat(profile.skills().get(0).category()).isEqualTo(SkillCategory.LANGUAGE);
        assertThat(profile.skills().get(0).level()).isEqualTo(SkillLevel.ADVANCED);
        assertThat(profile.skills().get(2).category()).isNull();
        assertThat(profile.experiences()).singleElement().satisfies((Experience e) -> {
            assertThat(e.period().start()).isEqualTo(YearMonth.of(2021, 3));
            assertThat(e.period().end()).isNull();
            assertThat(e.technologies()).containsExactly("Spring Boot", "PostgreSQL");
        });
        assertThat(profile.education()).singleElement().satisfies(e -> {
            assertThat(e.period().start()).isEqualTo(YearMonth.of(2016, 1));
            assertThat(e.period().end()).isEqualTo(YearMonth.of(2021, 12));
        });
        assertThat(profile.certifications()).singleElement()
                .satisfies(c -> assertThat(c.issuedOn()).isEqualTo(YearMonth.of(2023, 7)));
    }

    @Test
    void readsTheSuggestedSearch() {
        var suggestion = reader.readSuggestion("""
                {"keywords": "desarrollador backend java spring boot",
                 "alternatives": ["ingeniero de software java", 5, "desarrollador java"],
                 "rationale": "Tu experiencia reciente es backend con Spring."}
                """);

        assertThat(suggestion.keywords()).isEqualTo("desarrollador backend java spring boot");
        assertThat(suggestion.alternatives()).containsExactly("ingeniero de software java", "desarrollador java");
    }

    @Test
    void readsTheInferredWorkProfileAndIgnoresAnInvalidOneWithoutLosingTheSearch() {
        var suggestion = reader.readSuggestion("""
                {"keywords": "desarrollador backend java",
                 "workProfile": {"primaryRole": "backend", "secondaryRoles": ["FULLSTACK", "BACKEND", "PILOTO", 3],
                                 "seniority": "JUNIOR", "title": "Desarrollador backend Java"}}
                """);

        WorkProfile work = suggestion.workProfile();
        assertThat(work.primaryRole()).isEqualTo(WorkRole.BACKEND);
        assertThat(work.secondaryRoles()).containsExactly(WorkRole.FULLSTACK);
        assertThat(work.seniority()).isEqualTo(Seniority.JUNIOR);
        assertThat(work.source()).isEqualTo(WorkProfile.Source.AI);

        assertThat(reader.readSuggestion("""
                {"keywords": "contador", "workProfile": {"primaryRole": "OTHER", "title": null}}
                """).workProfile()).isNull();
        assertThat(reader.readSuggestion("{\"keywords\": \"java\"}").workProfile()).isNull();
    }

    @Test
    void somethingThatIsNotTheAskedJsonIsRejected() {
        assertThatThrownBy(() -> reader.readProfile("No puedo ayudarte con eso."))
                .isInstanceOf(InvalidAiOutputException.class);
        assertThatThrownBy(() -> reader.readProfile("{\"skills\": [}")).isInstanceOf(InvalidAiOutputException.class);
        assertThatThrownBy(() -> reader.readSuggestion("{\"alternatives\": []}"))
                .isInstanceOf(InvalidAiOutputException.class);
    }
}

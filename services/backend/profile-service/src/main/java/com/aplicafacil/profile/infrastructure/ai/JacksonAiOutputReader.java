package com.aplicafacil.profile.infrastructure.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Function;

import org.springframework.stereotype.Component;

import com.aplicafacil.profile.application.port.out.AiOutputReader;
import com.aplicafacil.profile.domain.Certification;
import com.aplicafacil.profile.domain.Education;
import com.aplicafacil.profile.domain.Experience;
import com.aplicafacil.profile.domain.Language;
import com.aplicafacil.profile.domain.ProfileContent;
import com.aplicafacil.profile.domain.SearchSuggestion;
import com.aplicafacil.profile.domain.Seniority;
import com.aplicafacil.profile.domain.Skill;
import com.aplicafacil.profile.domain.SkillCategory;
import com.aplicafacil.profile.domain.SkillLevel;
import com.aplicafacil.profile.domain.WorkProfile;
import com.aplicafacil.profile.domain.WorkRole;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Lee el JSON que piden los prompts de {@code ProfilePrompts}. Los modelos a veces lo envuelven en texto o en
 * {@code ```json}: se toma desde la primera llave hasta la última. Una entrada inválida (sin nombre, sin empresa…) se
 * descarta y el resto sigue.
 */
@Component
public class JacksonAiOutputReader implements AiOutputReader {

    private static final int MAX_ITEMS = 60;

    private final JsonMapper json;

    public JacksonAiOutputReader(JsonMapper json) {
        this.json = json;
    }

    @Override
    public ProfileContent readProfile(String output) {
        JsonNode root = object(output);
        return new ProfileContent(
                Json.text(root, "headline"),
                Json.text(root, "summary"),
                list(root, "skills", JacksonAiOutputReader::skill),
                list(root, "experiences", JacksonAiOutputReader::experience),
                list(root, "education", JacksonAiOutputReader::education),
                list(root, "languages", n -> Language.of(Json.text(n, "name"), Json.text(n, "proficiency"))),
                list(root, "certifications", n -> Certification.of(Json.text(n, "name"), Json.text(n, "issuer"),
                        Json.month(n, "issuedOn"))));
    }

    @Override
    public SearchSuggestion readSuggestion(String output) {
        JsonNode root = object(output);
        List<String> alternatives = new ArrayList<>();
        root.path("alternatives").valueStream().filter(JsonNode::isString).forEach(a -> alternatives.add(a.asString()));
        try {
            return new SearchSuggestion(Json.text(root, "keywords"), alternatives, Json.text(root, "rationale"),
                    workProfile(root.path("workProfile")));
        } catch (IllegalArgumentException e) {
            throw new InvalidAiOutputException("La búsqueda sugerida no trae keywords", e);
        }
    }

    /** Un perfil laboral inválido (rol desconocido, OTHER sin título) se ignora: la búsqueda sugerida sigue valiendo. */
    private static WorkProfile workProfile(JsonNode node) {
        WorkRole primary = Json.enumValue(node, "primaryRole", WorkRole.class);
        if (primary == null) {
            return null;
        }
        List<WorkRole> secondary = new ArrayList<>();
        node.path("secondaryRoles").valueStream()
                .map(n -> n.isString() ? enumOrNull(n.asString()) : null)
                .filter(Objects::nonNull)
                .filter(role -> role != primary && !secondary.contains(role))
                .limit(WorkProfile.MAX_SECONDARY)
                .forEach(secondary::add);
        try {
            return new WorkProfile(primary, secondary, Json.enumValue(node, "seniority", Seniority.class),
                    Json.text(node, "title"), WorkProfile.Source.AI, null);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static WorkRole enumOrNull(String value) {
        try {
            return WorkRole.valueOf(value.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private JsonNode object(String output) {
        JsonNode root;
        try {
            root = json.readTree(braces(output));
        } catch (JacksonException e) {
            throw new InvalidAiOutputException("JSON inválido: " + e.getOriginalMessage(), e);
        }
        if (!root.isObject()) {
            throw new InvalidAiOutputException("La respuesta no es un objeto JSON", null);
        }
        return root;
    }

    /** Desde la primera llave hasta la última: quita el texto o el bloque de código alrededor. */
    private static String braces(String output) {
        int start = output == null ? -1 : output.indexOf('{');
        int end = output == null ? -1 : output.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new InvalidAiOutputException("La respuesta no contiene un objeto JSON", null);
        }
        return output.substring(start, end + 1);
    }

    private static <T> List<T> list(JsonNode root, String field, Function<JsonNode, T> reader) {
        List<T> items = new ArrayList<>();
        for (JsonNode node : root.path(field)) {
            if (items.size() >= MAX_ITEMS) {
                break;
            }
            try {
                items.add(reader.apply(node));
            } catch (IllegalArgumentException e) {
                // Una entrada sin lo obligatorio no tumba el resto del CV
            }
        }
        return items;
    }

    private static Skill skill(JsonNode n) {
        return Skill.of(Json.text(n, "name"), Json.enumValue(n, "category", SkillCategory.class),
                Json.enumValue(n, "level", SkillLevel.class), Json.integer(n, "yearsOfExperience"));
    }

    private static Experience experience(JsonNode n) {
        List<String> technologies = new ArrayList<>();
        n.path("technologies").valueStream().filter(JsonNode::isString).forEach(t -> technologies.add(t.asString()));
        return Experience.of(Json.text(n, "title"), Json.text(n, "company"), Json.period(n), Json.text(n, "location"),
                Json.text(n, "description"), technologies);
    }

    private static Education education(JsonNode n) {
        return Education.of(Json.text(n, "institution"), Json.text(n, "degree"), Json.text(n, "fieldOfStudy"),
                Json.period(n), Json.text(n, "description"));
    }
}

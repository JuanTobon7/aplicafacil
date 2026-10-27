package com.aplicafacil.computrabajo.infrastructure.scraper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aplicafacil.computrabajo.application.exception.ConnectException;
import com.aplicafacil.computrabajo.application.port.out.PortalBrowser.LoginResult;
import com.aplicafacil.computrabajo.domain.ConnectStep;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Lee la respuesta NDJSON de {@code POST /portals/computrabajo/sessions/login}: eventos {@code step} (progreso), y al
 * final un {@code result} (con {@code identity.name}) o un {@code error}.
 */
final class LoginStream {

    private static final Logger log = LoggerFactory.getLogger(LoginStream.class);

    private final JsonMapper json;

    LoginStream(JsonMapper json) {
        this.json = json;
    }

    LoginResult read(InputStream body, Consumer<ConnectStep> onStep) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(body, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                LoginResult result = line.isBlank() ? null : handle(json.readTree(line), onStep);
                if (result != null) {
                    return result;
                }
            }
        }
        throw new ConnectException("BROWSER_FAILED", "El scraper cerró la conexión sin resultado.");
    }

    /** @return el resultado si el evento lo trae; {@code null} para seguir leyendo */
    private LoginResult handle(JsonNode event, Consumer<ConnectStep> onStep) {
        switch (event.path("type").asString("")) {
            case "step" -> onStep.accept(ConnectStep.valueOf(event.path("step").asString()));
            case "result" -> {
                return new LoginResult(event.path("identity").path("name").asString(null),
                        event.get("storageState").toString());
            }
            case "error" -> throw error(event);
            default -> log.debug("Evento desconocido del scraper: {}", event.path("type"));
        }
        return null;
    }

    static ConnectException error(JsonNode event) {
        return new ConnectException(event.path("code").asString("BROWSER_FAILED"),
                event.path("message").asString("Falló el navegador."));
    }
}

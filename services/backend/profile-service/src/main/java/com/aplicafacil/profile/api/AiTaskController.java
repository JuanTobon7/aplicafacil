package com.aplicafacil.profile.api;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.aplicafacil.profile.application.ai.AiTaskQueries;
import com.aplicafacil.profile.application.ai.CvImportService;
import com.aplicafacil.profile.application.ai.SearchPreferenceService;
import com.aplicafacil.profile.application.port.out.CvTextExtractor.CvFile;
import com.aplicafacil.profile.domain.AiTask;
import com.aplicafacil.profile.domain.AiTaskKind;
import com.aplicafacil.profile.domain.AiTaskStatus;

/**
 * Lo que se le pide a la IA (vía mcp-gateway): importar un CV y elegir la búsqueda. Responden 202 con la tarea; el
 * front consulta {@code /profiles/me/ai-tasks/{id}} hasta que deja de estar PENDING.
 */
@RestController
@RequestMapping("/profiles/me")
@PreAuthorize("hasRole('USER')")
class AiTaskController {

    private final CvImportService cvs;
    private final SearchPreferenceService searches;
    private final AiTaskQueries tasks;

    AiTaskController(CvImportService cvs, SearchPreferenceService searches, AiTaskQueries tasks) {
        this.cvs = cvs;
        this.searches = searches;
        this.tasks = tasks;
    }

    @PostMapping(path = "/cv", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<AiTaskResponse> importCv(@AuthenticationPrincipal Jwt jwt, @RequestPart("file") MultipartFile file,
                                            @RequestParam("configId") UUID configId) {
        CvFile cv;
        try {
            cv = new CvFile(file.getOriginalFilename(), file.getContentType(), file.getBytes());
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer el archivo subido", e);
        }
        return ResponseEntity.accepted().body(AiTaskResponse.of(cvs.importCv(userId(jwt), configId, cv)));
    }

    @PostMapping("/search-suggestion")
    ResponseEntity<AiTaskResponse> suggestSearch(@AuthenticationPrincipal Jwt jwt,
                                                 @Valid @RequestBody SuggestionRequest request) {
        return ResponseEntity.accepted().body(AiTaskResponse.of(
                searches.requestSuggestion(userId(jwt), request.configId())));
    }

    @GetMapping("/ai-tasks")
    List<AiTaskResponse> recent(@AuthenticationPrincipal Jwt jwt) {
        return tasks.recent(userId(jwt)).stream().map(AiTaskResponse::of).toList();
    }

    @GetMapping("/ai-tasks/{taskId}")
    AiTaskResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID taskId) {
        return AiTaskResponse.of(tasks.get(userId(jwt), taskId));
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    record SuggestionRequest(@NotNull UUID configId) {
    }

    record AiTaskResponse(UUID id, AiTaskKind kind, AiTaskStatus status, String fileName, String errorCode,
                          String errorMessage, Instant createdAt, Instant deadline, Instant completedAt) {

        static AiTaskResponse of(AiTask task) {
            AiTask.Outcome o = task.outcome();
            return new AiTaskResponse(task.id(), task.kind(), o.status(), task.fileName(), o.errorCode(),
                    o.errorMessage(), task.window().createdAt(), task.window().deadline(), o.completedAt());
        }
    }
}

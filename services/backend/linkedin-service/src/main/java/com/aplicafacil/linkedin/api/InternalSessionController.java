package com.aplicafacil.linkedin.api;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.aplicafacil.linkedin.application.port.out.LinkedInSessionProvider;
import com.aplicafacil.linkedin.application.services.ConnectionStateService;
import com.aplicafacil.linkedin.domain.JobListing;
import com.aplicafacil.linkedin.domain.JobSearchCriteria;
import com.aplicafacil.linkedin.domain.LinkedInAction.SearchJobs;
import com.aplicafacil.linkedin.domain.LinkedInAction.SessionCheck;
import com.aplicafacil.linkedin.domain.LinkedInAction.ValidateSession;

/** Endpoints entre servicios (solo tokens de servicio). El gateway no enruta {@code /internal/**}. */
@RestController
@RequestMapping("/internal")
class InternalSessionController {

    /** Varias páginas con pausas entre ellas; el cliente del scraper corta a los 6 minutos. */
    private static final Duration SEARCH_TIMEOUT = Duration.ofMinutes(6);

    private final LinkedInSessionProvider sessionProvider;
    private final ConnectionStateService connections;

    InternalSessionController(LinkedInSessionProvider sessionProvider,
                              ConnectionStateService connections) {
        this.sessionProvider = sessionProvider;
        this.connections = connections;
    }

    /** "Continuar como…": identity-service valida la sesión antes de emitir tokens. */
    @PostMapping("/sessions/{userId}/validate")
    ValidationResponse validate(@PathVariable UUID userId) throws InterruptedException {
        try {
            SessionCheck check = sessionProvider.execute(userId, new ValidateSession()).get(80, TimeUnit.SECONDS);
            return new ValidationResponse(SessionValidationResult.of(check));
        } catch (ExecutionException e) {
            return SessionValidationResult.ofFailure(e.getCause())
                    .map(ValidationResponse::new)
                    .orElseThrow(() -> new IllegalStateException("No se pudo validar la sesión", e.getCause()));
        } catch (TimeoutException e) {
            throw new IllegalStateException("La validación de la sesión tardó demasiado", e);
        }
    }

    /**
     * Worker de jobs-service: busca ofertas con la sesión del usuario. Los fallos conocidos (NOT_CONNECTED,
     * SESSION_BUSY, SESSION_EXPIRED, SEARCH_FAILED…) salen con su {@code code} por {@code LinkedInExceptionHandler}.
     */
    @PostMapping("/sessions/{userId}/job-search")
    JobSearchResponse searchJobs(@PathVariable UUID userId, @Valid @RequestBody JobSearchRequest request)
            throws InterruptedException {
        JobSearchCriteria criteria = new JobSearchCriteria(request.keywords().strip(),
                request.location() == null || request.location().isBlank() ? null : request.location().strip(),
                request.maxResults(), request.postedWithinDays(), Boolean.TRUE.equals(request.remote()));
        try {
            List<JobListing> jobs = sessionProvider.execute(userId, new SearchJobs(criteria))
                    .get(SEARCH_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
            return new JobSearchResponse(jobs);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof RuntimeException known) {
                throw known;
            }
            throw new IllegalStateException("La búsqueda falló", e.getCause());
        } catch (TimeoutException e) {
            throw new IllegalStateException("La búsqueda en LinkedIn tardó demasiado", e);
        }
    }

    @DeleteMapping("/sessions/{userId}")
    ResponseEntity<Void> delete(@PathVariable UUID userId) {
        connections.deleteAll(userId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/connections/statuses")
    Map<UUID, String> statuses(@RequestBody List<UUID> userIds) {
        return connections.statuses(userIds);
    }

    record ValidationResponse(SessionValidationResult result) {
    }

    record JobSearchRequest(
            @NotBlank @Size(min = 2, max = 200) String keywords,
            @Size(max = 120) String location,
            @Min(1) @Max(200) int maxResults,
            @Min(1) @Max(30) Integer postedWithinDays,
            Boolean remote) {
    }

    record JobSearchResponse(List<JobListing> jobs) {
    }
}

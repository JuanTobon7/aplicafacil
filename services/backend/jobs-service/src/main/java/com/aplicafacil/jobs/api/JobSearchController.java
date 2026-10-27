package com.aplicafacil.jobs.api;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.aplicafacil.jobs.application.JobSearchService;
import com.aplicafacil.jobs.application.JobSearchRejectedException;
import com.aplicafacil.jobs.application.JobSearchView;
import com.aplicafacil.jobs.application.port.out.FoundPosting;
import com.aplicafacil.jobs.domain.JobPlatform;
import com.aplicafacil.jobs.domain.SearchCriteria;

/**
 * Búsquedas de vacantes ({@code /api/jobs/searches} en el gateway, que quita {@code /api/jobs}). Pedir una búsqueda
 * responde 202: el worker la procesa en segundo plano y el front consulta el estado hasta COMPLETED o FAILED.
 */
@RestController
@RequestMapping("/searches")
@PreAuthorize("hasRole('USER')")
class JobSearchController {

    private final JobSearchService searches;

    JobSearchController(JobSearchService searches) {
        this.searches = searches;
    }

    @PostMapping
    ResponseEntity<JobSearchView> request(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody SearchRequest request) {
        SearchCriteria criteria;
        try {
            criteria = new SearchCriteria(request.keywords(), request.location(),
                    request.maxResults() == null ? 25 : request.maxResults(), request.postedWithinDays(),
                    Boolean.TRUE.equals(request.remote()));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
        return ResponseEntity.accepted().body(searches.request(userId(jwt), request.platform(), criteria));
    }

    @GetMapping
    List<JobSearchView> recent(@AuthenticationPrincipal Jwt jwt) {
        return searches.recent(userId(jwt));
    }

    @GetMapping("/{id}")
    JobSearchView get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return searches.get(userId(jwt), id);
    }

    @GetMapping("/{id}/postings")
    List<FoundPosting> postings(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return searches.results(userId(jwt), id);
    }

    @ExceptionHandler
    ResponseEntity<ProblemDetail> rejected(JobSearchRejectedException e) {
        HttpStatus status = switch (e.reason()) {
            case UNSUPPORTED_PLATFORM -> HttpStatus.BAD_REQUEST;
            case TOO_MANY_PENDING -> HttpStatus.TOO_MANY_REQUESTS;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
        };
        return ResponseEntity.status(status).body(ProblemDetail.forStatusAndDetail(status, e.getMessage()));
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    record SearchRequest(
            @NotNull JobPlatform platform,
            @NotBlank @Size(min = 2, max = 200) String keywords,
            @Size(max = 120) String location,
            @Min(1) @Max(SearchCriteria.MAX_RESULTS) Integer maxResults,
            @Min(1) @Max(30) Integer postedWithinDays,
            Boolean remote) {
    }
}

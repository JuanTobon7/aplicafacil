package com.aplicafacil.aiconfig.api;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.aplicafacil.aiconfig.application.ProviderConfigLifecycle;
import com.aplicafacil.aiconfig.application.ProviderConfigQueries;
import com.aplicafacil.aiconfig.application.ProviderConfigService;
import com.aplicafacil.aiconfig.application.ProviderConfigService.ConfigChanges;
import com.aplicafacil.aiconfig.application.ProviderConfigService.NewConfig;
import com.aplicafacil.aiconfig.application.ProviderConfigView;
import com.aplicafacil.aiconfig.domain.ModelParameters;
import com.aplicafacil.aiconfig.domain.ProviderType;

/**
 * API que ve el front ({@code /api/ai/**} en el gateway). El usuario sale del token; la API key solo entra
 * (alta y rotación) y nunca sale.
 */
@RestController
@RequestMapping("/ai")
class ProviderConfigController {

    private final ProviderConfigService service;
    private final ProviderConfigLifecycle lifecycle;
    private final ProviderConfigQueries queries;

    ProviderConfigController(ProviderConfigService service, ProviderConfigLifecycle lifecycle,
                             ProviderConfigQueries queries) {
        this.service = service;
        this.lifecycle = lifecycle;
        this.queries = queries;
    }

    @GetMapping("/provider-types")
    List<ProviderType> providerTypes() {
        return queries.providerTypes();
    }

    @GetMapping("/provider-configs")
    List<ProviderConfigView> list(@AuthenticationPrincipal Jwt jwt) {
        return queries.list(userId(jwt));
    }

    @GetMapping("/provider-configs/{id}")
    ProviderConfigView get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return queries.get(userId(jwt), id);
    }

    @PostMapping("/provider-configs")
    ResponseEntity<ProviderConfigView> create(@AuthenticationPrincipal Jwt jwt,
                                              @Valid @RequestBody CreateRequest request) {
        ProviderConfigView view = service.create(userId(jwt), new NewConfig(request.providerType(),
                request.displayName(), request.apiKey(), request.model(), request.baseUrl(), request.parameters(),
                request.enabled() == null || request.enabled()));
        return ResponseEntity.status(HttpStatus.CREATED).body(view);
    }

    @PutMapping("/provider-configs/{id}")
    ProviderConfigView update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                              @Valid @RequestBody UpdateRequest request) {
        return service.update(userId(jwt), id, new ConfigChanges(request.displayName(), request.model(),
                request.baseUrl(), request.parameters(), request.expectedVersion()));
    }

    @PutMapping("/provider-configs/{id}/credential")
    ProviderConfigView rotate(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                              @Valid @RequestBody RotateRequest request) {
        return lifecycle.rotateCredential(userId(jwt), id, request.apiKey(), request.expectedVersion());
    }

    @PutMapping("/provider-configs/{id}/enabled")
    ProviderConfigView setEnabled(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                  @Valid @RequestBody EnabledRequest request) {
        return service.setEnabled(userId(jwt), id, request.enabled());
    }

    @DeleteMapping("/provider-configs/{id}")
    ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        lifecycle.delete(userId(jwt), id);
        return ResponseEntity.noContent().build();
    }

    /** 202: el resultado llega por el estado de la configuración ({@code lastTest}). */
    @PostMapping("/provider-configs/{id}/test")
    ResponseEntity<ProviderConfigView> test(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return ResponseEntity.accepted().body(lifecycle.requestTest(userId(jwt), id));
    }

    static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    /** Sin espacios ni saltos de línea: una key pegada con un salto no se guarda corrupta. */
    static final String API_KEY_PATTERN = "^[\\x21-\\x7E]{8,512}$";

    record CreateRequest(
            @NotBlank @Pattern(regexp = "^[A-Z][A-Z0-9_]{1,31}$") String providerType,
            @NotBlank @Size(max = 100) String displayName,
            @NotBlank @Pattern(regexp = API_KEY_PATTERN, message = "API key inválida") String apiKey,
            @Size(max = 200) String model,
            @Size(max = 2048) String baseUrl,
            @Valid ModelParameters parameters,
            Boolean enabled) {

        @Override
        public String toString() {
            return "CreateRequest[providerType=" + providerType + ", displayName=" + displayName + "]";
        }
    }

    record UpdateRequest(
            @NotBlank @Size(max = 100) String displayName,
            @Size(max = 200) String model,
            @Size(max = 2048) String baseUrl,
            @Valid ModelParameters parameters,
            Long expectedVersion) {
    }

    record RotateRequest(
            @NotBlank @Pattern(regexp = API_KEY_PATTERN, message = "API key inválida") String apiKey,
            Long expectedVersion) {

        @Override
        public String toString() {
            return "RotateRequest[expectedVersion=" + expectedVersion + "]";
        }
    }

    record EnabledRequest(@NotNull Boolean enabled) {
    }
}

package com.aplicafacil.aiconfig.api;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.aplicafacil.aiconfig.application.AiRequestService;
import com.aplicafacil.aiconfig.application.AiRequestService.AiRequestView;
import com.aplicafacil.aiconfig.application.AiRequestService.NewRequest;
import com.aplicafacil.aiconfig.domain.ModelParameters;

/** Solicitudes de IA asíncronas: 202 al enviarla y polling de {@code GET /ai/requests/{id}} hasta un estado final. */
@RestController
@RequestMapping("/ai/requests")
class AiRequestController {

    private final AiRequestService service;

    AiRequestController(AiRequestService service) {
        this.service = service;
    }

    @PostMapping
    ResponseEntity<AiRequestView> submit(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody SubmitRequest request) {
        AiRequestView view = service.submit(ProviderConfigController.userId(jwt), new NewRequest(request.configId(),
                request.prompt(), request.systemPrompt(), request.model(), request.parameters()));
        return ResponseEntity.accepted().body(view);
    }

    @GetMapping("/{id}")
    AiRequestView get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return service.get(ProviderConfigController.userId(jwt), id);
    }

    @GetMapping
    List<AiRequestView> recent(@AuthenticationPrincipal Jwt jwt) {
        return service.recent(ProviderConfigController.userId(jwt));
    }

    record SubmitRequest(
            @NotNull UUID configId,
            @NotBlank @Size(max = 32_000) String prompt,
            @Size(max = 8_000) String systemPrompt,
            @Size(max = 200) String model,
            @Valid ModelParameters parameters) {
    }
}

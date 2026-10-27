package com.aplicafacil.linkedin.api;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.aplicafacil.linkedin.application.services.SessionCheckService;
import com.aplicafacil.linkedin.application.services.ConnectAttempt.AttemptView;
import com.aplicafacil.linkedin.application.services.ConnectAttemptRegistry;
import com.aplicafacil.linkedin.application.services.ConnectService;
import com.aplicafacil.linkedin.application.services.ConnectionStateService;
import com.aplicafacil.linkedin.application.services.ConnectionStateService.StatusView;
import com.aplicafacil.linkedin.application.services.StatusBroadcaster;

/**
 * API de conexión que ve el front ({@code /api/linkedin/**} en el gateway).
 *
 * <p>Conectar es también iniciar sesión, así que {@code POST /linkedin/connect} y el seguimiento del intento
 * no exigen JWT: el {@code attemptId} es un UUID aleatorio y el gateway exige el token local. Si llega un
 * JWT, el intento es una reconexión de ese usuario.
 */
@RestController
@RequestMapping("/linkedin")
class LinkedInController {

    private final ConnectService connectService;
    private final ConnectAttemptRegistry attempts;
    private final ConnectionStateService connections;
    private final StatusBroadcaster broadcaster;
    private final SessionCheckService sessionCheck;

    LinkedInController(ConnectService connectService, ConnectAttemptRegistry attempts,
                       ConnectionStateService connections, StatusBroadcaster broadcaster,
                       SessionCheckService sessionCheck) {
        this.connectService = connectService;
        this.sessionCheck = sessionCheck;
        this.attempts = attempts;
        this.connections = connections;
        this.broadcaster = broadcaster;
    }

    /** Pide al scraper abrir el navegador visible en la página oficial de LinkedIn. */
    @PostMapping("/connect")
    ResponseEntity<AttemptView> connect(@AuthenticationPrincipal Jwt jwt) {
        UUID expected = jwt == null ? null : UUID.fromString(jwt.getSubject());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(connectService.start(expected));
    }

    /** Estado del navegador del scraper: el front avisa mientras se instala Chromium en el primer arranque. */
    @GetMapping("/browser")
    BrowserStatus browser() {
        return new BrowserStatus(connectService.browserState().name());
    }

    /** Polling (respaldo de SSE). */
    @GetMapping("/connect/{attemptId}")
    AttemptView attempt(@PathVariable UUID attemptId) {
        return attempts.get(attemptId).view();
    }

    @GetMapping(
            path = "/connect/{attemptId}/events",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE
    )
    SseEmitter attemptEvents(@PathVariable UUID attemptId) {
        return attempts.subscribe(attemptId);
    }

    @DeleteMapping("/connect/{attemptId}")
    ResponseEntity<Void> cancel(@PathVariable UUID attemptId) {
        connectService.cancel(attemptId);
        return ResponseEntity.accepted().build();
    }

    /** DISCONNECTED | CONNECTING | CONNECTED | EXPIRED del usuario autenticado. */
    @GetMapping("/status")
    StatusView status(@AuthenticationPrincipal Jwt jwt) {
        return connections.status(userId(jwt));
    }

    @GetMapping(path = "/status/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    SseEmitter statusEvents(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = userId(jwt);
        return broadcaster.subscribe(userId, connections.status(userId));
    }

    /** Al abrir la app: comprueba que la sesión guardada siga viva (contracts/openapi/linkedin-api.yaml). */
    @PostMapping("/session/check")
    SessionCheckResponse checkSession(@AuthenticationPrincipal Jwt jwt) throws InterruptedException {
        return new SessionCheckResponse(sessionCheck.check(userId(jwt)));
    }

    /** Borra la sesión guardada; la cuenta de la plataforma se conserva. */
    @PostMapping("/disconnect")
    ResponseEntity<Void> disconnect(@AuthenticationPrincipal Jwt jwt) {
        connections.disconnect(userId(jwt));
        return ResponseEntity.noContent().build();
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    record BrowserStatus(String state) {
    }

    record SessionCheckResponse(SessionCheckService.Validity result) {
    }
}

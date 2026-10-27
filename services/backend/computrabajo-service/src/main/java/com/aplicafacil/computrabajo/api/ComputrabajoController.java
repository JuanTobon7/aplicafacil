package com.aplicafacil.computrabajo.api;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.aplicafacil.computrabajo.application.services.ConnectAttempt.AttemptView;
import com.aplicafacil.computrabajo.application.services.ConnectService;
import com.aplicafacil.computrabajo.application.services.ConnectionService;
import com.aplicafacil.computrabajo.application.services.ConnectionService.StatusView;
import com.aplicafacil.computrabajo.application.services.SessionCheckService;
import com.aplicafacil.computrabajo.application.services.SessionCheckService.Validity;

/** contracts/openapi/computrabajo-api.yaml. Todo se limita al usuario del token. */
@RestController
@RequestMapping("/computrabajo")
class ComputrabajoController {

    private final ConnectService connect;
    private final ConnectionService connections;
    private final SessionCheckService sessionCheck;

    ComputrabajoController(ConnectService connect, ConnectionService connections, SessionCheckService sessionCheck) {
        this.connect = connect;
        this.connections = connections;
        this.sessionCheck = sessionCheck;
    }

    @GetMapping("/status")
    StatusView status(@AuthenticationPrincipal Jwt jwt) {
        return connections.status(userId(jwt));
    }

    @PostMapping("/connect")
    ResponseEntity<AttemptView> connect(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(connect.start(userId(jwt)));
    }

    @GetMapping("/connect/{attemptId}")
    AttemptView attempt(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID attemptId) {
        return connect.attempt(userId(jwt), attemptId);
    }

    @DeleteMapping("/connect/{attemptId}")
    ResponseEntity<Void> cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID attemptId) {
        connect.cancel(userId(jwt), attemptId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/session/check")
    SessionCheckResponse checkSession(@AuthenticationPrincipal Jwt jwt) {
        return new SessionCheckResponse(sessionCheck.check(userId(jwt)));
    }

    @DeleteMapping("/connection")
    ResponseEntity<Void> disconnect(@AuthenticationPrincipal Jwt jwt) {
        connections.disconnect(userId(jwt));
        return ResponseEntity.noContent().build();
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    record SessionCheckResponse(Validity result) {
    }
}

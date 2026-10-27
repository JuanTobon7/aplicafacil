package com.aplicafacil.computrabajo.api;

import java.util.Map;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.aplicafacil.computrabajo.application.exception.ConnectException;

/** ProblemDetail con {@code code}: el front lo traduce a un mensaje y una acción. */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
class ComputrabajoExceptionHandler {

    private static final Map<String, HttpStatus> STATUS = Map.of(
            "ALREADY_CONNECTING", HttpStatus.CONFLICT,
            "ATTEMPT_NOT_FOUND", HttpStatus.NOT_FOUND,
            "BROWSER_NOT_READY", HttpStatus.SERVICE_UNAVAILABLE,
            "SCRAPER_UNAVAILABLE", HttpStatus.SERVICE_UNAVAILABLE,
            "DATABASE_UNAVAILABLE", HttpStatus.SERVICE_UNAVAILABLE);

    @ExceptionHandler(ConnectException.class)
    ResponseEntity<ProblemDetail> connect(ConnectException ex) {
        HttpStatus status = STATUS.getOrDefault(ex.code(), HttpStatus.BAD_GATEWAY);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, ex.getMessage());
        problem.setProperty("code", ex.code());
        return ResponseEntity.status(status).body(problem);
    }
}

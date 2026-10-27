package com.aplicafacil.linkedin.api;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.aplicafacil.linkedin.application.exception.ConnectException;
import com.aplicafacil.linkedin.application.exception.NotConnectedException;
import com.aplicafacil.linkedin.application.exception.SessionBusyException;
import com.aplicafacil.linkedin.application.exception.SessionExpiredException;

/** Errores con un {@code code} estable que el front traduce a mensaje y acción. */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
class LinkedInExceptionHandler {

    @ExceptionHandler(ConnectException.class)
    ResponseEntity<ProblemDetail> connect(ConnectException ex) {
        return problem(ex.status(), ex.code(), ex.getMessage());
    }

    @ExceptionHandler(SessionExpiredException.class)
    ResponseEntity<ProblemDetail> expired(SessionExpiredException ex) {
        return problem(HttpStatus.CONFLICT, "SESSION_EXPIRED", "Tu sesión de LinkedIn expiró. Vuelve a conectar tu cuenta.");
    }

    @ExceptionHandler(SessionBusyException.class)
    ResponseEntity<ProblemDetail> busy(SessionBusyException ex) {
        return problem(HttpStatus.CONFLICT, "SESSION_BUSY", "Hay una automatización usando esta cuenta.");
    }

    @ExceptionHandler(NotConnectedException.class)
    ResponseEntity<ProblemDetail> notConnected(NotConnectedException ex) {
        return problem(HttpStatus.CONFLICT, "NOT_CONNECTED", "Esta cuenta no tiene una sesión de LinkedIn guardada.");
    }

    private static ResponseEntity<ProblemDetail> problem(HttpStatus status, String code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setProperty("code", code);
        return ResponseEntity.status(status).body(problem);
    }
}

package com.aplicafacil.aiconfig.api;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.aplicafacil.aiconfig.application.exception.AiConfigException;

/** Errores con un {@code code} estable que el front traduce a mensaje y acción. */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
class AiConfigExceptionHandler {

    @ExceptionHandler(AiConfigException.class)
    ResponseEntity<ProblemDetail> handle(AiConfigException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(ex.status(), ex.getMessage());
        problem.setProperty("code", ex.code());
        return ResponseEntity.status(ex.status()).body(problem);
    }
}

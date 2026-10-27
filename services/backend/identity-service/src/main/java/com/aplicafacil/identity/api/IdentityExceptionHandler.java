package com.aplicafacil.identity.api;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.aplicafacil.identity.application.exception.IdentityException;

/** {@link IdentityException} → ProblemDetail con un {@code code} estable para el front. */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
class IdentityExceptionHandler {

    @ExceptionHandler(IdentityException.class)
    ResponseEntity<ProblemDetail> handle(IdentityException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(ex.status(), ex.getMessage());
        problem.setProperty("code", ex.code());
        return ResponseEntity.status(ex.status()).body(problem);
    }
}

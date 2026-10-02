package com.aplicafacil.people.infrastructure.web;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.aplicafacil.people.application.exception.EmailAlreadyUsedException;
import com.aplicafacil.people.application.exception.PeopleAccessDeniedException;
import com.aplicafacil.people.application.exception.PeopleAlreadyRegisteredException;
import com.aplicafacil.people.application.exception.PeopleNotFoundException;
import com.aplicafacil.people.domain.exception.DomainException;

/**
 * Traduce las excepciones de dominio y de aplicación a HTTP (ProblemDetail).
 * Ni el dominio ni application conocen códigos HTTP. Lo genérico (validación
 * de {@code @Valid}) lo resuelve el {@code GlobalExceptionHandler} de platform-common.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
class PeopleExceptionHandler {

    @ExceptionHandler(PeopleNotFoundException.class)
    ProblemDetail handleNotFound(PeopleNotFoundException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(PeopleAccessDeniedException.class)
    ProblemDetail handleAccessDenied(PeopleAccessDeniedException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    @ExceptionHandler({EmailAlreadyUsedException.class, PeopleAlreadyRegisteredException.class})
    ProblemDetail handleConflict(RuntimeException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(DomainException.class)
    ProblemDetail handleDomain(DomainException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }
}

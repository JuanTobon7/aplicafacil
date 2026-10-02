package com.aplicafacil.auth.api.rest;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.aplicafacil.auth.application.exception.EmailAlreadyRegisteredException;
import com.aplicafacil.auth.application.exception.InvalidCredentialsException;
import com.aplicafacil.auth.application.exception.UserNotFoundException;

/** Errores de la API de auth como ProblemDetail (RFC 9457). */
@RestControllerAdvice(basePackageClasses = AuthApiExceptionHandler.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
class AuthApiExceptionHandler {

    @ExceptionHandler(UserNotFoundException.class)
    ProblemDetail notFound(UserNotFoundException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(EmailAlreadyRegisteredException.class)
    ProblemDetail conflict(EmailAlreadyRegisteredException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    ProblemDetail invalidCredentials(InvalidCredentialsException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(AccountController.NotAUserTokenException.class)
    ProblemDetail notAUser(AccountController.NotAUserTokenException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    /** Reglas del dominio (política de contraseñas, formatos). */
    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail invalidInput(IllegalArgumentException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }
}

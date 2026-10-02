package com.aplicafacil.platform.web;

/** Recurso inexistente → HTTP 404 (ver {@link GlobalExceptionHandler}). */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}

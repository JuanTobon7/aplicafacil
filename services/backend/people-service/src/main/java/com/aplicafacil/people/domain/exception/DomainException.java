package com.aplicafacil.people.domain.exception;

/**
 * Base de las violaciones de invariantes del dominio: un valor inválido nunca llega a un modelo.
 * Se detectan mirando solo el objeto; lo que requiere consultar datos vive en application.
 */
public abstract class DomainException extends RuntimeException {

    protected DomainException(String message) {
        super(message);
    }
}

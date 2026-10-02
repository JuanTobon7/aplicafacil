package com.aplicafacil.auth.application.port;

import com.aplicafacil.auth.domain.UserRegistered;

/**
 * Publica eventos de usuario hacia otros servicios. La implementación debe
 * publicar solo si la transacción que guardó al usuario se confirma.
 */
public interface UserEventPublisher {

    void publish(UserRegistered event);
}

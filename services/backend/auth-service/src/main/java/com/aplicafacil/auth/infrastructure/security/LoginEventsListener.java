package com.aplicafacil.auth.infrastructure.security;

import java.util.UUID;

import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.stereotype.Component;

import com.aplicafacil.auth.application.LoginAttemptService;

/**
 * Traduce los eventos de autenticación de Spring Security a
 * {@link LoginAttemptService}. Solo cuenta logins de usuarios con formulario
 * (no autenticaciones de clientes OAuth ni validaciones de JWT).
 */
@Component
class LoginEventsListener {

    private final LoginAttemptService loginAttempts;

    LoginEventsListener(LoginAttemptService loginAttempts) {
        this.loginAttempts = loginAttempts;
    }

    @EventListener
    void onSuccess(AuthenticationSuccessEvent event) {
        if (event.getAuthentication() instanceof UsernamePasswordAuthenticationToken authentication) {
            // el nombre del principal es el id del usuario (AuthUserDetailsService)
            loginAttempts.recordSuccess(UUID.fromString(authentication.getName()));
        }
    }

    @EventListener
    void onBadCredentials(AuthenticationFailureBadCredentialsEvent event) {
        if (event.getAuthentication() instanceof UsernamePasswordAuthenticationToken authentication) {
            // en un fallo el nombre es lo que se escribió en el formulario: el email
            loginAttempts.recordFailure(authentication.getName());
        }
    }
}

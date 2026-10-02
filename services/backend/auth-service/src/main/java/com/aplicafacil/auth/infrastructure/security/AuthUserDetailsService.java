package com.aplicafacil.auth.infrastructure.security;

import java.time.Clock;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;

import com.aplicafacil.auth.application.port.UserRepository;
import com.aplicafacil.auth.domain.User;

/**
 * Login con email + contraseña.
 *
 * <p>El {@code UserDetails} devuelto usa el <b>id</b> del usuario como username:
 * así el nombre del principal (y el {@code sub} de los tokens) es un UUID
 * estable que no cambia si el usuario cambia su email. Se usa la clase
 * estándar de Spring a propósito: Spring Authorization Server serializa el
 * principal en {@code oauth2_authorization} y solo admite tipos conocidos.
 */
@Component
class AuthUserDetailsService implements UserDetailsService {

    private final UserRepository users;
    private final Clock clock;

    AuthUserDetailsService(UserRepository users, Clock clock) {
        this.users = users;
        this.clock = clock;
    }

    @Override
    public UserDetails loadUserByUsername(String email) {
        User user = users.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("Credenciales inválidas"));
        return org.springframework.security.core.userdetails.User
                .withUsername(user.getId().toString())
                .password(user.getPasswordHash())
                .roles(user.getRoles().stream().map(Enum::name).toArray(String[]::new))
                .disabled(!user.isEnabled())
                .accountLocked(user.isLocked(clock.instant()))
                .build();
    }
}

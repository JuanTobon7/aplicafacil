package com.aplicafacil.auth.infrastructure.security;

import java.util.EnumSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.aplicafacil.auth.application.RegisterUserUseCase;
import com.aplicafacil.auth.application.port.UserRepository;
import com.aplicafacil.auth.domain.Role;

/**
 * Crea el administrador inicial si está configurado y todavía no existe.
 * Pasa por el registro normal: misma política de contraseñas y mismo evento
 * hacia people-service.
 */
@Component
@Order(2)
class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final AuthProperties properties;
    private final UserRepository users;
    private final RegisterUserUseCase registerUser;

    AdminBootstrap(AuthProperties properties, UserRepository users, RegisterUserUseCase registerUser) {
        this.properties = properties;
        this.users = users;
        this.registerUser = registerUser;
    }

    @Override
    public void run(ApplicationArguments args) {
        AuthProperties.BootstrapAdmin admin = properties.bootstrapAdmin();
        if (admin == null || !admin.isConfigured()) {
            return;
        }
        if (users.existsByEmail(admin.email())) {
            return;
        }
        try {
            registerUser.register(new RegisterUserUseCase.Command(
                    admin.email(), admin.password(), "Admin", "AplicaFacil", EnumSet.of(Role.USER, Role.ADMIN)));
            log.info("Administrador inicial creado: {}", admin.email());
        } catch (IllegalArgumentException e) {
            // p. ej. la contraseña configurada no cumple la política: el servicio sigue arrancando
            log.error("No se creó el administrador inicial {}: {}", admin.email(), e.getMessage());
        }
    }
}

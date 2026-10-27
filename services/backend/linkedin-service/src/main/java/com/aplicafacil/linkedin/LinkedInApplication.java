package com.aplicafacil.linkedin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Sesión de LinkedIn: captura con Playwright, almacenamiento cifrado en PostgreSQL, lectura del
 * perfil y ejecución de acciones con la sesión del usuario (ADR 0003).
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class LinkedInApplication {

    public static void main(String[] args) {
        SpringApplication.run(LinkedInApplication.class, args);
    }
}

package com.aplicafacil.identity;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Identidad: LinkedIn es el registro y el login. Emite los JWT propios de la plataforma (ADR 0003). */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class IdentityApplication {

    public static void main(String[] args) {
        SpringApplication.run(IdentityApplication.class, args);
    }
}

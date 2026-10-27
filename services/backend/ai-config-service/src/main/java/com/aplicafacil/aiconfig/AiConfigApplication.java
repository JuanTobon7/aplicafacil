package com.aplicafacil.aiconfig;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Configuración de proveedores de IA por usuario (ADR 0005): guarda las API keys cifradas, publica los cambios
 * (sin secretos) para el mcp-gateway y mantiene el estado que este informa.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class AiConfigApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiConfigApplication.class, args);
    }
}

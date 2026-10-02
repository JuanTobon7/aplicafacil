package com.aplicafacil.automation;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Orquestador de la automatización: scheduler, comandos al scraper y agente LLM. */
@SpringBootApplication
public class AutomationApplication {

    public static void main(String[] args) {
        SpringApplication.run(AutomationApplication.class, args);
    }
}

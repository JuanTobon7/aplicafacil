package com.aplicafacil.profile.infrastructure.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

/**
 * {@code aplicafacil.profile.ai.*}; lo que falte toma el valor por defecto de {@code ProfileAiPolicy}.
 *
 * @param timeout     cuánto esperar la respuesta del mcp-gateway (por defecto 3 min)
 * @param maxCvChars  texto del CV que se manda a la IA (por defecto 24 000 caracteres)
 * @param maxFileSize tamaño máximo del archivo (por defecto 10 MB; también lo limita spring.servlet.multipart)
 */
@ConfigurationProperties("aplicafacil.profile.ai")
public record ProfileAiProperties(Duration timeout, int maxCvChars, DataSize maxFileSize) {
}

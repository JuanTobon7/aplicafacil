package com.aplicafacil.linkedin.infrastructure.scraper;

import java.time.Duration;

import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.aplicafacil.linkedin.infrastructure.client.ServiceTokenProvider;
import com.aplicafacil.linkedin.infrastructure.config.LinkedInProperties;

/** Los dos clientes HTTP hacia el scraper (127.0.0.1, con token de servicio), según cuánto puede tardar la llamada. */
@Component
class ScraperClients {

    private final RestClient quick;
    private final RestClient longRunning;

    ScraperClients(RestClient.Builder builder, LinkedInProperties properties, ServiceTokenProvider tokens) {
        this.quick = client(builder, properties, tokens, Duration.ofSeconds(90));
        // El inicio de sesión dura hasta loginTimeout; se deja un margen
        this.longRunning = client(builder, properties, tokens, properties.loginTimeout().plusMinutes(1));
    }

    /** Validar, leer el perfil, salud del navegador. */
    RestClient quick() {
        return quick;
    }

    /** Inicio de sesión y búsquedas que recorren varias páginas. */
    RestClient longRunning() {
        return longRunning;
    }

    private static RestClient client(RestClient.Builder builder, LinkedInProperties properties,
                                     ServiceTokenProvider tokens, Duration readTimeout) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(readTimeout);
        return builder.clone()
                .baseUrl(properties.scraperUrl())
                .requestFactory(factory)
                .requestInterceptor(tokens.interceptor())
                .build();
    }
}

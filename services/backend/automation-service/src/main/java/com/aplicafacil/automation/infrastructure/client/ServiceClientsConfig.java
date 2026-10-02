package com.aplicafacil.automation.infrastructure.client;

import org.springframework.cloud.client.loadbalancer.DeferringLoadBalancerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.client.OAuth2ClientHttpRequestInterceptor;
import org.springframework.web.client.RestClient;

/**
 * Clientes HTTP hacia los otros microservicios.
 *
 * <ul>
 *   <li>Load balancing: resuelve {@code http://jobs-service} vía Eureka.</li>
 *   <li>OAuth2 client_credentials: cada request lleva un token de servicio
 *       emitido por auth-service al cliente {@code automation-service}.</li>
 * </ul>
 *
 * El builder NO se publica como bean a propósito: reemplazaría el
 * {@code RestClient.Builder} global de Boot y Spring AI (OpenAI, MCP)
 * terminaría enviando sus llamadas por Eureka y con el token de auth-service.
 *
 * Se usa {@link AuthorizedClientServiceOAuth2AuthorizedClientManager} (y no el
 * manager por defecto) porque el worker corre en tareas programadas, fuera de
 * cualquier request HTTP.
 */
@Configuration
public class ServiceClientsConfig {

    static final String REGISTRATION_ID = "auth";

    @Bean
    OAuth2AuthorizedClientManager serviceAuthorizedClientManager(
            ClientRegistrationRepository registrations,
            OAuth2AuthorizedClientService authorizedClients) {
        var manager = new AuthorizedClientServiceOAuth2AuthorizedClientManager(registrations, authorizedClients);
        manager.setAuthorizedClientProvider(
                OAuth2AuthorizedClientProviderBuilder.builder().clientCredentials().build());
        return manager;
    }

    @Bean
    RestClient peopleClient(DeferringLoadBalancerInterceptor loadBalancer,
                            OAuth2AuthorizedClientManager serviceAuthorizedClientManager) {
        return serviceClient("http://people-service", loadBalancer, serviceAuthorizedClientManager);
    }

    @Bean
    RestClient profileClient(DeferringLoadBalancerInterceptor loadBalancer,
                             OAuth2AuthorizedClientManager serviceAuthorizedClientManager) {
        return serviceClient("http://profile-service", loadBalancer, serviceAuthorizedClientManager);
    }

    @Bean
    RestClient jobsClient(DeferringLoadBalancerInterceptor loadBalancer,
                          OAuth2AuthorizedClientManager serviceAuthorizedClientManager) {
        return serviceClient("http://jobs-service", loadBalancer, serviceAuthorizedClientManager);
    }

    private static RestClient serviceClient(String baseUrl,
                                            DeferringLoadBalancerInterceptor loadBalancer,
                                            OAuth2AuthorizedClientManager manager) {
        var oauth2 = new OAuth2ClientHttpRequestInterceptor(manager);
        oauth2.setClientRegistrationIdResolver(request -> REGISTRATION_ID);
        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestInterceptor(oauth2)
                .requestInterceptor(loadBalancer)
                .build();
    }
}

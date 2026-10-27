package com.aplicafacil.aiconfig.application;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import com.aplicafacil.aiconfig.domain.ProviderType;

/**
 * Lo que los casos de uso necesitan de la configuración; lo arma infrastructure a partir de sus propiedades.
 *
 * @param credentialReaders {@code sub} de los tokens de servicio que pueden leer la API key descifrada
 */
public record AiPolicy(List<ProviderType> providerTypes, List<String> credentialReaders, Duration requestTimeout,
                       int maxConfigsPerUser) {

    public AiPolicy {
        providerTypes = List.copyOf(providerTypes);
        credentialReaders = List.copyOf(credentialReaders);
    }

    public Optional<ProviderType> providerType(String type) {
        return providerTypes.stream().filter(t -> t.type().equals(type)).findFirst();
    }

    public boolean canReadCredentials(String serviceSubject) {
        return credentialReaders.contains(serviceSubject);
    }
}

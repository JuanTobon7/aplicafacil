package com.aplicafacil.aiconfig.application.port.out;

import java.util.List;
import java.util.UUID;

import com.aplicafacil.aiconfig.domain.AiRequest;
import com.aplicafacil.aiconfig.domain.ModelParameters;
import com.aplicafacil.aiconfig.domain.ProviderConfig;

/**
 * Mensajes para el mcp-gateway, registrados en la misma transacción que el cambio (outbox). Ningún mensaje lleva
 * la API key.
 */
public interface AiEventOutbox {

    enum ConfigChange { CREATED, UPDATED, DISABLED, DELETED, CREDENTIAL_ROTATED }

    /** {@code ai.provider-config.*}. */
    void configChanged(ConfigChange change, ProviderConfig config);

    /** {@code ai.provider.test}: pide un health_check; el resultado vuelve con el mismo correlationId. */
    void testRequested(ProviderConfig config, UUID correlationId);

    /** {@code ai.request.submit}. */
    void requestSubmitted(AiRequest request, List<ChatMessage> messages, ModelParameters parameters, String model);

    record ChatMessage(String role, String content) {
    }
}

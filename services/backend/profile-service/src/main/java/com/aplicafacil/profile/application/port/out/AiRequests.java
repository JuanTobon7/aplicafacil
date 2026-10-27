package com.aplicafacil.profile.application.port.out;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Pide al mcp-gateway una respuesta de IA ({@code ai.request.submit}). Se guarda en la transacción en curso y se
 * publica después (outbox): si la transacción se deshace, la solicitud no sale.
 */
public interface AiRequests {

    void submit(AiPrompt prompt);

    /**
     * @param requestId clave de idempotencia; el resultado vuelve con el mismo id
     * @param maxTokens tope de la respuesta ({@code null} = el de la configuración del proveedor)
     */
    record AiPrompt(UUID requestId, UUID userId, UUID configId, List<Message> messages, Instant submittedAt,
                    Instant deadline, Double temperature, Integer maxTokens) {
    }

    /** @param role system, user o assistant */
    record Message(String role, String content) {
    }
}

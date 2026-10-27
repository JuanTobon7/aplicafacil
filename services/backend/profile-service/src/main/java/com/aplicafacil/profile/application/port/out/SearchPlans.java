package com.aplicafacil.profile.application.port.out;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Avisa a jobs-service qué búsquedas lanzar ({@code profile.search-plan.ready}, en la transacción de quien llama). */
public interface SearchPlans {

    void publish(SearchPlan plan);

    /**
     * @param planId   id de la tarea de IA que lo generó: jobs-service no lanza dos veces el mismo plan
     * @param searches palabras clave, la principal primero
     */
    record SearchPlan(UUID planId, UUID userId, List<String> searches, boolean remoteOnly, Integer postedWithinDays,
                      String location, Instant occurredAt) {

        public SearchPlan {
            searches = List.copyOf(searches);
        }
    }
}

package com.aplicafacil.jobs.application.port.out;

import java.util.List;
import java.util.UUID;

import com.aplicafacil.jobs.domain.JobCard;
import com.aplicafacil.jobs.domain.JobPlatform;

/** Guarda las ofertas encontradas: una sola vez por (portal, externalId), y las asocia a la búsqueda. */
public interface JobPostingStore {

    /**
     * Inserta las nuevas, actualiza las conocidas ({@code last_seen_at}, título…) y registra el orden en la búsqueda.
     * Idempotente: repetir la misma búsqueda no duplica nada.
     *
     * @return cuántas ofertas no existían antes
     */
    int saveAll(UUID searchId, JobPlatform platform, List<JobCard> cards);
}

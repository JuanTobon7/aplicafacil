package com.aplicafacil.jobs.application.port.out;

import java.util.List;
import java.util.UUID;

import com.aplicafacil.jobs.domain.JobCard;
import com.aplicafacil.jobs.domain.JobPlatform;
import com.aplicafacil.jobs.domain.SearchCriteria;

/**
 * Estrategia por portal de empleo: cómo pedir una búsqueda a quien maneja el navegador.
 *
 * <ul>
 *   <li>{@code LinkedInJobPortalGateway}: a través de linkedin-service, dueño de la sesión cifrada (lease por usuario).</li>
 *   <li>{@code ComputrabajoJobPortalGateway}: directo al scraper; el listado es público.</li>
 * </ul>
 *
 * Contrato común (Liskov): devuelve las cards en el orden del portal o lanza {@link JobPortalException} con un
 * código estable y si vale la pena reintentar. Nunca devuelve {@code null}.
 */
public interface JobPortalGateway {

    JobPlatform platform();

    List<JobCard> search(UUID userId, SearchCriteria criteria);
}

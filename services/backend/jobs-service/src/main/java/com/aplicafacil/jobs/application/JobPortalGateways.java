package com.aplicafacil.jobs.application;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.aplicafacil.jobs.application.port.out.JobPortalGateway;
import com.aplicafacil.jobs.application.port.out.JobPortalException;
import com.aplicafacil.jobs.domain.JobPlatform;

/**
 * Registro de estrategias: Spring inyecta todos los {@link JobPortalGateway} y aquí se indexan por plataforma.
 * Agregar un portal = una clase {@code @Component} nueva; nada más cambia.
 */
@Component
public class JobPortalGateways {

    private final Map<JobPlatform, JobPortalGateway> byPlatform = new EnumMap<>(JobPlatform.class);

    public JobPortalGateways(List<JobPortalGateway> gateways) {
        for (JobPortalGateway gateway : gateways) {
            if (byPlatform.put(gateway.platform(), gateway) != null) {
                throw new IllegalStateException("Dos gateways para " + gateway.platform());
            }
        }
    }

    public JobPortalGateway get(JobPlatform platform) {
        JobPortalGateway gateway = byPlatform.get(platform);
        if (gateway == null) {
            throw new JobPortalException("UNSUPPORTED_PLATFORM", "No hay integración con " + platform, false);
        }
        return gateway;
    }

    public Set<JobPlatform> supported() {
        return byPlatform.keySet();
    }
}

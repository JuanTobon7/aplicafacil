package com.aplicafacil.aiconfig.application.port.out;

import java.util.UUID;

/** Lo que el alta y la edición consultan antes de guardar: el límite por persona y que el nombre no se repita. */
public interface ConfigQuota {

    long countByUser(UUID userId);

    /** Sin distinguir mayúsculas. */
    boolean nameTaken(UUID userId, String displayName);
}

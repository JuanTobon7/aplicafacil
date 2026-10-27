package com.aplicafacil.aiconfig.domain;

/**
 * Estado operativo de una configuración. Lo decide ai-config-service con los eventos del mcp-gateway.
 *
 * <ul>
 *   <li>{@code PENDING}: guardada; el mcp-gateway aún no la procesa.</li>
 *   <li>{@code INITIALIZING}: el mcp-gateway está creando el adaptador.</li>
 *   <li>{@code ACTIVE}: el adaptador validó la credencial y está listo.</li>
 *   <li>{@code FAILED}: la inicialización falló (ver {@code status_code}).</li>
 *   <li>{@code DISABLED}: la persona la desactivó.</li>
 * </ul>
 */
public enum ProviderConfigStatus {
    PENDING,
    INITIALIZING,
    ACTIVE,
    FAILED,
    DISABLED
}

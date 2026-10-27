package com.aplicafacil.profile.domain;

/**
 * Tipo de perfil laboral (contracts/openapi/profile-api.yaml, {@code WorkRole}). Agregar uno es agregar una constante
 * aquí y su nombre en el front; {@link #OTHER} cubre cualquier perfil fuera del catálogo, con su título.
 */
public enum WorkRole {
    BACKEND,
    FRONTEND,
    FULLSTACK,
    MOBILE,
    /** También SRE y cloud. */
    DEVOPS,
    DATA_ENGINEERING,
    /** También machine learning e IA. */
    DATA_SCIENCE,
    DATA_ANALYSIS,
    QA,
    UX_UI,
    SECURITY,
    EMBEDDED,
    GAME_DEV,
    /** Product owner, project manager, scrum master. */
    PRODUCT,
    IT_SUPPORT,
    OTHER
}

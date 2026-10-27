package com.aplicafacil.linkedin.contract;

/** De dónde viene la identidad. Decide cuál identificador de LinkedIn es el principal. */
public enum IdentitySource {
    /** Fase local: sesión capturada con Playwright → {@code linkedin_member_urn}. */
    SESSION,
    /** Fase desplegada: userinfo de OpenID Connect → {@code linkedin_oidc_sub}. */
    OIDC
}

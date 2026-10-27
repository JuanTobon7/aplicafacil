package com.aplicafacil.linkedin.contract;

import java.util.Objects;

/**
 * Identidad de un miembro de LinkedIn tal como la entrega un {@link LinkedInIdentityProvider}.
 *
 * @param source        proveedor que la obtuvo
 * @param memberUrn     {@code urn:li:fsd_profile:...}; obligatorio si {@code source = SESSION}
 * @param oidcSub       {@code sub} del userinfo; obligatorio si {@code source = OIDC}
 * @param email         puede ser {@code null} si LinkedIn no lo expone
 * @param emailVerified solo un correo verificado sirve para vincular cuentas entre modos
 * @param basics        datos básicos del perfil
 */
public record LinkedInIdentity(
        IdentitySource source,
        String memberUrn,
        String oidcSub,
        String email,
        boolean emailVerified,
        ProfileBasics basics) {

    public LinkedInIdentity {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(basics, "basics");
        if (source == IdentitySource.SESSION && isBlank(memberUrn)) {
            throw new IllegalArgumentException("Una identidad de sesión requiere memberUrn");
        }
        if (source == IdentitySource.OIDC && isBlank(oidcSub)) {
            throw new IllegalArgumentException("Una identidad OIDC requiere oidcSub");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}

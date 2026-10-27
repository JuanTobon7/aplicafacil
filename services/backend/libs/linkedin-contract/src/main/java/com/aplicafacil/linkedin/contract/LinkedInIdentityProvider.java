package com.aplicafacil.linkedin.contract;

/**
 * Obtiene la identidad y los datos del perfil de un miembro de LinkedIn.
 *
 * <ul>
 *   <li>{@code SessionIdentityProvider} (linkedin-service, fase local): los lee del contexto de Playwright.</li>
 *   <li>{@code OidcIdentityProvider} (identity-service, fase desplegada): los lee del userinfo de OIDC.</li>
 * </ul>
 *
 * @param <C> contexto del que se lee (BrowserContext, OidcUser, etc.)
 */
public interface LinkedInIdentityProvider<C> {

    IdentitySource source();

    /** Identidad mínima. Si falla, no se puede crear el usuario. */
    LinkedInIdentity readIdentity(C context);

    /** Perfil completo para postulaciones. Nunca lanza por secciones faltantes (ver {@link ProfileSnapshot#complete()}). */
    ProfileSnapshot readProfile(C context);
}

import type { BrowserSession } from "../../../application/port/out/browser-session";
import type { PortalProfileReader } from "../../../application/port/out/portal-profile-reader";
import { PROFILE_SECTIONS, incompleteProfile, type ProfileSnapshot } from "../../../domain/profile-snapshot";

/**
 * Lectura del perfil completo para postulaciones (experiencia, educación, habilidades, idiomas, "Acerca de",
 * correo y ubicación), con los selectores en {@link LinkedInSelectors}.
 *
 * Etapa 2: todavía no lee las secciones; devuelve un perfil incompleto para que el usuario se cree con los
 * datos mínimos y "Sincronizar de nuevo" lo complete en la etapa 3.
 */
export class LinkedInProfileReader implements PortalProfileReader {
  async read(_session: BrowserSession): Promise<ProfileSnapshot> {
    return incompleteProfile([...PROFILE_SECTIONS]);
  }
}

import type { ProfileSnapshot } from "../../../domain/profile-snapshot";
import type { BrowserSession } from "./browser-session";

/**
 * Perfil profesional completo. Lo que no se pudo leer va en `missingSections` con `complete=false`. Puede lanzar
 * ante fallos del navegador; quien llama decide si degrada a un perfil incompleto.
 */
export interface PortalProfileReader {
  read(session: BrowserSession): Promise<ProfileSnapshot>;
}

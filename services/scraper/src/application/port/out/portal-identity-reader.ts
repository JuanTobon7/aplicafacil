import type { BrowserSession } from "./browser-session";

/** Identidad de la cuenta con la sesión iniciada. Devuelve null (no lanza) si no se puede leer. */
export interface PortalIdentityReader<TIdentity extends object> {
  read(session: BrowserSession): Promise<TIdentity | null>;
}

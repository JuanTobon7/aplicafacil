import type { BrowserSession } from "./browser-session";

/**
 * Decide si hay sesión iniciada en el portal. Contrato para todas las implementaciones (Liskov):
 * "no hay sesión" se responde con false, nunca con una excepción; solo los fallos del navegador se propagan.
 */
export interface PortalSessionDetector {
  /** Durante el login visible: sin navegar, mirando la URL actual y las cookies. */
  isLoginComplete(session: BrowserSession): Promise<boolean>;
  /** Con una sesión guardada (headless): puede navegar para comprobarlo. */
  hasValidSession(session: BrowserSession): Promise<boolean>;
}

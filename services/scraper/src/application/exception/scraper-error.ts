/** Códigos estables que viajan a linkedin-service (contracts/openapi/scraper-browser.yaml). */
export type ScraperErrorCode =
  | "WINDOW_CLOSED"
  | "LOGIN_TIMEOUT"
  | "CANCELLED"
  | "IDENTITY_UNREADABLE"
  | "BROWSER_FAILED"
  | "BROWSER_NOT_READY"
  | "ALREADY_CONNECTING"
  | "UNKNOWN_PORTAL"
  /** El portal exige sesión para buscar y no llegó storageState. */
  | "SESSION_REQUIRED"
  /** El listado no apareció (el sitio cambió o bloqueó la búsqueda). */
  | "SEARCH_FAILED"
  /** No se pudo abrir el portal (sin red, DNS, timeout). */
  | "PORTAL_UNREACHABLE"
  | "INVALID_REQUEST";

export class ScraperError extends Error {
  constructor(
    readonly code: ScraperErrorCode,
    message: string,
  ) {
    super(message);
    this.name = "ScraperError";
  }
}

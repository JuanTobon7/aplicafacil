/** Configuración por variables de entorno (dev-up.ps1 las define). */
export const scraperProperties = {
  /** Solo loopback: el scraper nunca queda expuesto en la red. */
  host: "127.0.0.1",
  port: Number(process.env.SCRAPER_PORT ?? 8090),
  /** Emisor de los tokens de servicio (identity-service). */
  issuer: process.env.AUTH_ISSUER_URI ?? "http://127.0.0.1:9000",
  jwksUrl: process.env.AUTH_JWKS_URI ?? "http://127.0.0.1:9000/.well-known/jwks.json",
  audience: "aplicafacil-api",
  /**
   * Servicios que pueden usar el navegador. jobs-service solo busca en portales públicos (sin storageState): las
   * búsquedas con sesión de LinkedIn pasan por linkedin-service, dueño de la sesión (ADR 0006). computrabajo-service
   * es dueño de la sesión de candidato de Computrabajo (ADR 0008).
   */
  allowedClients: ["linkedin-service", "computrabajo-service", "jobs-service"],
  defaultLoginTimeoutMs: 5 * 60_000,
  /** Subdominio de Computrabajo (co, mx, pe, ar, cl…). */
  computrabajoCountry: process.env.COMPUTRABAJO_COUNTRY ?? "co",
  /** Tope de ofertas por búsqueda y de páginas recorridas. */
  maxSearchResults: 200,
  maxSearchPages: Number(process.env.SCRAPER_MAX_SEARCH_PAGES ?? 10),
  /** true: las búsquedas se ven en una ventana del navegador (dev-up -ShowBrowser). */
  showSearchBrowser: process.env.SCRAPER_SHOW_BROWSER === "true",
  /** Carpeta donde se graba un video de cada búsqueda (dev-up -Record); vacía: no se graba. */
  recordDir: process.env.SCRAPER_RECORD_DIR || undefined,
  /** Pausa (ms) antes de cada acción mientras se graba, para que el video se pueda seguir. */
  recordingSlowMoMs: Number(process.env.SCRAPER_SLOW_MO_MS ?? 0),
  /** Ventana al grabar, "ANCHOxALTO" (360x780: vertical para reels); vacía: 1280x800. */
  recordingViewport: parseSize(process.env.SCRAPER_RECORD_VIEWPORT),
  /** Densidad de píxeles al grabar (3 con 360x780 da un video 1080x2340). */
  recordingScale: Number(process.env.SCRAPER_RECORD_SCALE ?? 1),
};

function parseSize(value: string | undefined) {
  const match = /^(\d+)x(\d+)$/.exec(value ?? "");
  return match ? { width: Number(match[1]), height: Number(match[2]) } : undefined;
}

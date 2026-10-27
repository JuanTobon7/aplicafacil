/**
 * Todo lo que depende del sitio de LinkedIn en un solo lugar: URLs, cookies, endpoints internos y
 * selectores. El DOM de LinkedIn cambia seguido; cuando algo deje de funcionar, se corrige aquí.
 */
export const LinkedInSelectors = {
  origin: "https://www.linkedin.com",
  loginUrl: "https://www.linkedin.com/login",
  feedUrl: "https://www.linkedin.com/feed/",
  ownProfileUrl: "https://www.linkedin.com/in/me/",

  /** Cookie de sesión: existe solo con la sesión iniciada. */
  sessionCookie: "li_at",
  /** Su valor (sin comillas) es el token CSRF de la API interna. */
  csrfCookie: "JSESSIONID",

  feedPath: "/feed",
  loggedOutPaths: ["/login", "/authwall", "/uas/login", "/signup"],
  challengePaths: ["/checkpoint", "/challenge"],

  /** Datos del miembro con la sesión iniciada (API interna de LinkedIn). */
  meEndpoint: "https://www.linkedin.com/voyager/api/me",
  voyagerAccept: "application/vnd.linkedin.normalized+json+2.1",

  /** Respaldo si falla la API: el id del miembro aparece en el HTML de /in/me/. */
  memberUrnInHtml: /urn:li:fsd_profile:([A-Za-z0-9_-]{10,})/,
  profileNameHeading: "main h1",
} as const;

/**
 * Listado de empleos con sesión iniciada. LinkedIn redirige /jobs/search a /jobs/search-results (su interfaz nueva, de
 * clases con hash que cambian en cada despliegue): por eso los selectores usan el atributo `componentkey` y la
 * estructura de la card, no clases. Validado contra el DOM capturado el 2026-10-24 (npm run capture:dom). Si la
 * búsqueda devuelve SEARCH_FAILED o cero ofertas, se vuelve a capturar y se ajusta aquí.
 *
 * Card: título (dos spans: uno oculto para lectores de pantalla y el visible), empresa, ubicación y, abajo,
 * "Publicado hace …" y "Solicitud sencilla".
 */
export const LinkedInJobSelectors = {
  searchUrl: "https://www.linkedin.com/jobs/search/",
  pageSize: 25,
  /** f_WT (tipo de lugar de trabajo): 1 presencial, 2 remoto, 3 híbrido. */
  remoteWorkplaceType: "2",
  /** El div interno de cada card (el externo repite el mismo componentkey). */
  card: 'div[componentkey^="job-card-component-ref-"] > div[componentkey^="job-card-component-ref-"]',
  cardIdAttribute: "componentkey",
  /** "job-card-component-ref-4482171599" → 4482171599 */
  cardIdPattern: /job-card-component-ref-(\d+)/,
  noResults: ".jobs-search-no-results-banner, .jobs-search-two-pane__no-results-banner--expand",
  title: '[data-display-contents="true"] > p > span:last-child',
  company: '[data-display-contents="true"] + div > p',
  location: '[data-display-contents="true"] + div + p',
  /** En el texto completo de la card ("Publicado hace 2 días", "Posted 3 weeks ago"). */
  postedPattern:
    /(?:Publicado|Posted|Volvió a publicarse|Reposted)\s+((?:hace\s+)?\d+\s+(?:minutos?|horas?|días?|semanas?|meses|mes|años?|minutes?|hours?|days?|weeks?|months?|years?)(?:\s+ago)?)/,
  easyApplyLabels: ["easy apply", "solicitud sencilla"],
  /** Flecha del detalle con la ventana angosta (dom-snapshots/linkedin). */
  backToResults: 'button[aria-label="Volver a la lista de resultados"], button[aria-label="Back to search results"]',
} as const;

export const memberUrn =(id: string) => `urn:li:fsd_profile:${id}`;
export const publicProfileUrl = (publicIdentifier: string) => `${LinkedInSelectors.origin}/in/${publicIdentifier}/`;

export const isLoggedOutUrl = (url: string) => LinkedInSelectors.loggedOutPaths.some((p) => url.includes(p));
export const isChallengeUrl = (url: string) => LinkedInSelectors.challengePaths.some((p) => url.includes(p));

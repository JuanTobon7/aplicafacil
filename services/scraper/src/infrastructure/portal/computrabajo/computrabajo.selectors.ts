/**
 * Todo lo que depende del sitio de Computrabajo. El listado es público (no hace falta iniciar sesión).
 * Cada país tiene su subdominio: co, mx, pe, ar, cl, ec, ve… (COMPUTRABAJO_COUNTRY, por defecto co).
 *
 * Validado contra co.computrabajo.com el 2026-10-16 (20 ofertas por página, paginación ?p=N) y el 2026-10-23 (filtro
 * remoto "-en-remoto" y fecha "?pubdate=1|3|7|15|30", que se combinan con ?p=N). Si el sitio cambia y la
 * búsqueda devuelve SEARCH_FAILED o cero ofertas, se ajusta aquí.
 *
 * Cuidado con las listas de selectores ("a, b"): querySelector devuelve el primero en orden del documento, y un
 * elemento va antes que sus hijos. Por eso cada campo usa un selector único y preciso.
 */
export const ComputrabajoSelectors = {
  origin: (country: string) => `https://${country}.computrabajo.com`,
  pageSize: 20,
  /** Sufijo del filtro "Remoto" (checkbox `#remote`, `data-sem`). */
  remoteSuffix: "-en-remoto",
  /** Valores de "Fecha de publicación" (?pubdate=N): hoy, 3 días, semana, 15 días, mes. */
  pubdateOptions: [1, 3, 7, 15, 30],
  /** Cada oferta del listado; su id está en `data-id`. */
  card: "article.box_offer",
  cardIdAttribute: "data-id",
  noResults: ".box_empty, .no_results",
  title: "h2 a.js-o-link",
  /** Falta en las ofertas de empresas confidenciales. */
  company: "a[offer-grid-article-company-url]",
  /** El <p> de la empresa también tiene span.mr10 (la calificación): se excluye con :not(.dFlex). */
  location: "p.fs16.mt5:not(.dFlex) > span.mr10",
  posted: "p.fs13.fc_aux",
  /** El texto está en el span que contiene el ícono; falta cuando la empresa no publica el salario. */
  salary: "div.fs13 span.dIB:has(.i_salary)",
} as const;

/**
 * Sesión de candidato. Validado con un login real el 2026-10-24 (npm run capture:dom -- computrabajo): el acceso está en
 * candidato.{país}.computrabajo.com/acceso/ (también con Google, por secure.computrabajo.com) y al volver a la portada
 * queda la cookie AspNet.SharedCookie en .computrabajo.com y el bloque del usuario en la cabecera.
 */
export const ComputrabajoSessionSelectors = {
  loginUrl: (country: string) => `https://candidato.${country}.computrabajo.com/acceso/`,
  sessionCookie: "AspNet.SharedCookie",
  /** Páginas del flujo de acceso: mientras se está en ellas el login no terminó. */
  loginPaths: ["/acceso", "secure.computrabajo.com", "accounts.google."],
  /** Cabecera con la sesión iniciada: nombre (span) y foto. */
  userBox: "[data-info-user]",
  userName: "[data-info-user] > span",
} as const;

/** El menor filtro de fecha que cubre `days` (5 → 7). Sin días o más de un mes: sin filtro. */
export function pubdateFor(days: number | undefined): number | undefined {
  return days ? ComputrabajoSelectors.pubdateOptions.find((option) => option >= days) : undefined;
}

/** "Desarrollador Java" → "desarrollador-java" (así arma Computrabajo sus URLs de búsqueda). */
export function slug(text: string): string {
  return text
    .normalize("NFD")
    .replace(/[̀-ͯ]/g, "")
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "");
}

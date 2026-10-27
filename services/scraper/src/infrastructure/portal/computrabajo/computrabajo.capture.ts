import type { CaptureScript, LoginProbe } from "../../../application/port/out/dom-capture";
import { COMPUTRABAJO_PORTAL_ID } from "./computrabajo-job-search";
import { ComputrabajoSelectors, slug } from "./computrabajo.selectors";

/**
 * Cookies que suelen marcar una sesión iniciada; se confirma con el DOM capturado. No cuenta "session": el sitio crea
 * ASP.NET_SessionId a todo visitante, con o sin sesión.
 */
const AUTH_COOKIE = /auth|token|login|user|cand/i;

/** El primer enlace cuyo texto coincide con `text` (menú del área de candidato, por ejemplo). */
export function linkWithText(html: string, text: RegExp, origin: string): string | null {
  for (const [, href, inner] of html.matchAll(/<a\b[^>]*href="([^"#][^"]*)"[^>]*>([\s\S]*?)<\/a>/gi)) {
    if (text.test(inner.replace(/<[^>]+>/g, " "))) return new URL(href.replace(/&amp;/g, "&"), origin).toString();
  }
  return null;
}

/** El enlace de la primera oferta del listado (`h2 a.js-o-link`), para abrir su detalle. */
function firstOfferUrl(origin: string) {
  return (html: string): string | null => {
    const card = html.match(/<a[^>]*class="[^"]*js-o-link[^"]*"[^>]*>/)?.[0];
    const href = card?.match(/href="([^"]+)"/)?.[1];
    return href ? new URL(href.replace(/&amp;/g, "&"), origin).toString() : null;
  };
}

/**
 * Todavía no se conoce la cookie de sesión de Computrabajo (para eso es esta captura): se da por iniciada cuando aparece
 * una cookie de autenticación que no estaba en la portada. Si no se detecta, sirve la señal manual (dom-snapshots/
 * .continuar). El acceso está en candidato.{país}.computrabajo.com/acceso/, así que la URL sola no basta.
 */
function loggedIn({ url, cookieNames, initialCookieNames }: LoginProbe): boolean {
  const onLoginPage = /\/acceso\b/i.test(url);
  return !onLoginPage && cookieNames.some((name) => AUTH_COOKIE.test(name) && !initialCookieNames.includes(name));
}

/**
 * Páginas de Computrabajo que necesita el portal con sesión: la portada (dónde se inicia sesión), el área de candidato
 * (nombre y correo: lector de identidad), postulaciones y hoja de vida, y el listado y el detalle de una oferta.
 */
export function computrabajoCaptureScript(keywords: string, country = "co"): CaptureScript {
  const origin = ComputrabajoSelectors.origin(country);
  return {
    portal: COMPUTRABAJO_PORTAL_ID,
    displayName: "Computrabajo",
    // La portada: el botón "Ingresar" lleva al acceso que use el sitio en ese momento
    loginUrl: origin,
    origin,
    extraCookieOrigins: [origin.replace("://", "://candidato.")],
    loggedIn,
    steps: [
      { name: "area-candidato", description: "Página a la que llegaste al iniciar sesión" },
      {
        name: "mis-postulaciones",
        description: "Tus postulaciones",
        url: (html) => linkWithText(html, /postulaci/i, origin),
      },
      {
        name: "hoja-de-vida",
        description: "Tu hoja de vida",
        url: (html) => linkWithText(html, /hoja de vida|mi cv|curr[ií]culum/i, origin),
      },
      {
        name: "busqueda-empleos",
        description: `Listado de empleos para "${keywords}"`,
        url: new URL(`/trabajo-de-${slug(keywords)}`, origin).toString(),
        waitFor: ComputrabajoSelectors.card,
      },
      { name: "detalle-oferta", description: "Detalle de la primera oferta", url: firstOfferUrl(origin), waitFor: "body" },
    ],
  };
}

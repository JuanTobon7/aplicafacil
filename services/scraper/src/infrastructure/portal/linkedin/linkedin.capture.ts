import type { CaptureScript, LoginProbe } from "../../../application/port/out/dom-capture";
import { LinkedInJobSelectors, LinkedInSelectors, isChallengeUrl, isLoggedOutUrl } from "./linkedin.selectors";
import { LINKEDIN_PORTAL_ID } from "./linkedin.portal";

/** La primera oferta del listado (`data-occludable-job-id`), para abrir su detalle. */
function firstJobUrl(html: string): string | null {
  const id = html.match(/data-occludable-job-id="(\d+)"/)?.[1];
  return id ? `${LinkedInSelectors.origin}/jobs/view/${id}/` : null;
}

/** Igual que el detector de sesión: cookie li_at y una URL que no sea de login ni de verificación. */
function loggedIn({ url, cookieNames }: LoginProbe): boolean {
  return cookieNames.includes(LinkedInSelectors.sessionCookie) && !isLoggedOutUrl(url) && !isChallengeUrl(url);
}

/**
 * Páginas de LinkedIn que usa (o usará) el scraper: el feed (detector de sesión), el listado de empleos y una card
 * (búsqueda), el detalle de una oferta (postulación) y el propio perfil (lector del perfil, etapa 3).
 */
export function linkedInCaptureScript(keywords: string): CaptureScript {
  const search = new URL(LinkedInJobSelectors.searchUrl);
  search.searchParams.set("keywords", keywords);
  search.searchParams.set("location", "Colombia");
  return {
    portal: LINKEDIN_PORTAL_ID,
    displayName: "LinkedIn",
    loginUrl: LinkedInSelectors.loginUrl,
    origin: LinkedInSelectors.origin,
    loggedIn,
    steps: [
      { name: "feed", description: "Feed (la sesión está iniciada)", url: LinkedInSelectors.feedUrl, waitFor: "main" },
      {
        name: "busqueda-empleos",
        description: `Listado de empleos para "${keywords}"`,
        url: search.toString(),
        waitFor: LinkedInJobSelectors.card,
      },
      { name: "detalle-oferta", description: "Detalle de la primera oferta", url: firstJobUrl, waitFor: "main" },
      { name: "perfil", description: "Tu perfil", url: LinkedInSelectors.ownProfileUrl, waitFor: "main h1" },
    ],
  };
}

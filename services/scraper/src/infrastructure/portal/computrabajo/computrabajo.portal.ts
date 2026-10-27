import type { BrowserSession, SessionCookies } from "../../../application/port/out/browser-session";
import type { JobPortal } from "../../../application/port/out/job-portal";
import type { PortalIdentityReader } from "../../../application/port/out/portal-identity-reader";
import type { PortalProfileReader } from "../../../application/port/out/portal-profile-reader";
import type { PortalSessionDetector } from "../../../application/port/out/portal-session-detector";
import { withPage } from "../../../application/port/out/with-page";
import { incompleteProfile, PROFILE_SECTIONS } from "../../../domain/profile-snapshot";
import { COMPUTRABAJO_PORTAL_ID } from "./computrabajo-job-search";
import { ComputrabajoSelectors, ComputrabajoSessionSelectors } from "./computrabajo.selectors";

/** Lo que se sabe del candidato con la sesión iniciada: el nombre de la cabecera. */
export type ComputrabajoIdentity = { name: string };

async function hasSessionCookie(session: SessionCookies, origin: string): Promise<boolean> {
  return (await session.cookies(origin)).some(
    (c) => c.name === ComputrabajoSessionSelectors.sessionCookie && c.value.trim() !== "",
  );
}

/** Sesión = cookie AspNet.SharedCookie, fuera de las páginas de acceso (y en la portada, el bloque del usuario). */
class ComputrabajoSessionDetector implements PortalSessionDetector {
  constructor(private readonly origin: string) {}

  async isLoginComplete(session: BrowserSession): Promise<boolean> {
    const url = session.currentUrl() ?? "";
    const inLoginFlow = ComputrabajoSessionSelectors.loginPaths.some((path) => url.includes(path));
    return !inLoginFlow && (await hasSessionCookie(session, this.origin));
  }

  hasValidSession(session: BrowserSession): Promise<boolean> {
    return withPage(session, async (page) => {
      await page.goto(this.origin, { waitUntil: "domcontentloaded", timeoutMs: 45_000 });
      const userBox = await page.textOf(ComputrabajoSessionSelectors.userBox);
      return userBox !== null && (await hasSessionCookie(session, this.origin));
    });
  }
}

/** El nombre del candidato en la cabecera de la portada; null si no hay sesión o el navegador falla. */
class ComputrabajoIdentityReader implements PortalIdentityReader<ComputrabajoIdentity> {
  constructor(private readonly origin: string) {}

  async read(session: BrowserSession): Promise<ComputrabajoIdentity | null> {
    try {
      return await withPage(session, async (page) => {
        await page.goto(this.origin, { waitUntil: "domcontentloaded", timeoutMs: 45_000 });
        const name = (await page.textOf(ComputrabajoSessionSelectors.userName))?.replace(/\s+/g, " ").trim();
        return name ? { name } : null;
      });
    } catch {
      return null;
    }
  }
}

/** La hoja de vida de Computrabajo todavía no se lee: el perfil viene de LinkedIn o del CV. */
const notRead: PortalProfileReader = { read: async () => incompleteProfile(PROFILE_SECTIONS) };

/** Composición del portal Computrabajo con sesión de candidato (la búsqueda pública es ComputrabajoJobSearch). */
export function createComputrabajoPortal(country = "co"): JobPortal<ComputrabajoIdentity> {
  const origin = ComputrabajoSelectors.origin(country);
  return {
    id: COMPUTRABAJO_PORTAL_ID,
    displayName: "Computrabajo",
    loginUrl: ComputrabajoSessionSelectors.loginUrl(country),
    sessionDetector: new ComputrabajoSessionDetector(origin),
    identityReader: new ComputrabajoIdentityReader(origin),
    profileReader: notRead,
  };
}

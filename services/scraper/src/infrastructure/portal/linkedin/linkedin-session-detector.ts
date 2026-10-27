import type { SessionCookies, BrowserSession } from "../../../application/port/out/browser-session";
import { withPage } from "../../../application/port/out/with-page";
import type { PortalSessionDetector } from "../../../application/port/out/portal-session-detector";
import { LinkedInSelectors, isChallengeUrl, isLoggedOutUrl } from "./linkedin.selectors";

/** Sesión de LinkedIn = cookie li_at y una URL que no sea de login ni de verificación. */
export class LinkedInSessionDetector implements PortalSessionDetector {
  async isLoginComplete(session: BrowserSession): Promise<boolean> {
    const url = session.currentUrl() ?? "";
    const pending = isChallengeUrl(url) || isLoggedOutUrl(url);
    return ((await hasSessionCookie(session)) && !pending) || url.includes(LinkedInSelectors.feedPath);
  }

  /** Abre el feed y decide si la sesión sigue iniciada: sin login, sin verificación y con li_at. */
  hasValidSession(session: BrowserSession): Promise<boolean> {
    return withPage(session, async (page) => {
      await page.goto(LinkedInSelectors.feedUrl, { waitUntil: "domcontentloaded", timeoutMs: 45_000 });
      const url = page.url();
      return !isLoggedOutUrl(url) && !isChallengeUrl(url) && (await hasSessionCookie(session));
    });
  }
}

async function hasSessionCookie(session: SessionCookies): Promise<boolean> {
  return (await session.cookies(LinkedInSelectors.origin)).some(
    (c) => c.name === LinkedInSelectors.sessionCookie && c.value.trim() !== "",
  );
}

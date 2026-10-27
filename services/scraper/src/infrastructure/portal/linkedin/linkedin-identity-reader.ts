import type { BrowserSession } from "../../../application/port/out/browser-session";
import { withPage } from "../../../application/port/out/with-page";
import type { PortalIdentityReader } from "../../../application/port/out/portal-identity-reader";
import type { LinkedInIdentity } from "./linkedin-identity";
import { linkedInIdentity, parseMe } from "./linkedin-me-parser";
import { LinkedInSelectors } from "./linkedin.selectors";

/**
 * Identidad del miembro con la sesión. Primero la API interna /voyager/api/me; si falla, el HTML de /in/me/.
 * Nunca registra cookies ni respuestas completas.
 */
export class LinkedInIdentityReader implements PortalIdentityReader<LinkedInIdentity> {
  async read(session: BrowserSession): Promise<LinkedInIdentity | null> {
    return (await this.fromApi(session)) ?? (await this.fromProfilePage(session));
  }

  private async fromApi(session: BrowserSession): Promise<LinkedInIdentity | null> {
    try {
      const csrf = (await session.cookies(LinkedInSelectors.origin))
        .find((c) => c.name === LinkedInSelectors.csrfCookie)
        ?.value.replaceAll('"', "");
      if (!csrf) return null;
      const response = await session.get(LinkedInSelectors.meEndpoint, {
        "csrf-token": csrf,
        accept: LinkedInSelectors.voyagerAccept,
        "x-restli-protocol-version": "2.0.0",
      });
      if (!response.ok) return null;
      return parseMe((await response.json()) as Record<string, any>);
    } catch {
      return null;
    }
  }

  private async fromProfilePage(session: BrowserSession): Promise<LinkedInIdentity | null> {
    try {
      return await withPage(session, async (page) => {
        await page.goto(LinkedInSelectors.ownProfileUrl, { waitUntil: "domcontentloaded" });
        const match = LinkedInSelectors.memberUrnInHtml.exec(await page.html());
        if (!match) return null;
        const name = (await page.textOf(LinkedInSelectors.profileNameHeading))?.trim() ?? "";
        const [firstName, ...rest] = name.split(/\s+/);
        const url = page.url();
        return linkedInIdentity(match[1], {
          firstName: firstName || null,
          lastName: rest.join(" ") || null,
          headline: null,
          photoUrl: null,
          publicProfileUrl: url.includes("/in/") && !url.includes("/in/me") ? url : null,
          location: null,
        });
      });
    } catch {
      return null;
    }
  }
}

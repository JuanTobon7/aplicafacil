import { describe, expect, it } from "vitest";
import { parseMe } from "../../../../src/infrastructure/portal/linkedin/linkedin-me-parser";
import { createLinkedInPortal } from "../../../../src/infrastructure/portal/linkedin/linkedin.portal";
import { LinkedInSelectors, isChallengeUrl, isLoggedOutUrl } from "../../../../src/infrastructure/portal/linkedin/linkedin.selectors";
import { FakeBrowserSession } from "../../../support/fake-browser";
import { describeJobPortalContract } from "../job-portal.contract";

describeJobPortalContract("LinkedIn", createLinkedInPortal);

const liAt = { name: "li_at", value: "token" };

describe("parseMe", () => {
  it("lee el URN del miembro y los datos básicos de /voyager/api/me", () => {
    const identity = parseMe({
      data: { "*miniProfile": "urn:li:fs_miniProfile:ACoAAB1234567890" },
      included: [
        {
          entityUrn: "urn:li:fs_miniProfile:ACoAAB1234567890",
          firstName: "Ana",
          lastName: "Pérez",
          occupation: "Backend Developer",
          publicIdentifier: "ana-perez",
          picture: {
            "com.linkedin.common.VectorImage": {
              rootUrl: "https://media.licdn.com/dms/image/",
              artifacts: [{ fileIdentifyingUrlPathSegment: "100.jpg" }, { fileIdentifyingUrlPathSegment: "800.jpg" }],
            },
          },
        },
      ],
    });

    expect(identity).toEqual({
      source: "SESSION",
      memberUrn: "urn:li:fsd_profile:ACoAAB1234567890",
      oidcSub: null,
      email: null,
      emailVerified: false,
      basics: {
        firstName: "Ana",
        lastName: "Pérez",
        headline: "Backend Developer",
        photoUrl: "https://media.licdn.com/dms/image/800.jpg",
        publicProfileUrl: "https://www.linkedin.com/in/ana-perez/",
        location: null,
      },
    });
  });

  it("devuelve null sin miniProfile", () => {
    expect(parseMe({ data: {} })).toBeNull();
  });
});

describe("URLs de LinkedIn", () => {
  it("detecta login, authwall y verificación", () => {
    expect(isLoggedOutUrl("https://www.linkedin.com/login?session_redirect=x")).toBe(true);
    expect(isLoggedOutUrl("https://www.linkedin.com/authwall?trk=x")).toBe(true);
    expect(isChallengeUrl("https://www.linkedin.com/checkpoint/challenge/abc")).toBe(true);
    expect(isLoggedOutUrl("https://www.linkedin.com/feed/")).toBe(false);
  });
});

describe("LinkedInSessionDetector", () => {
  const { sessionDetector } = createLinkedInPortal();

  it("el login termina con li_at fuera de las páginas de login o verificación", async () => {
    const session = new FakeBrowserSession({ cookies: [liAt] });
    await (await session.newPage()).goto("https://www.linkedin.com/checkpoint/challenge/x");
    expect(await sessionDetector.isLoginComplete(session)).toBe(false);
    await (await session.newPage()).goto("https://www.linkedin.com/in/ana/");
    expect(await sessionDetector.isLoginComplete(session)).toBe(true);
  });

  it("la sesión guardada es válida si el feed no redirige al login", async () => {
    const valid = new FakeBrowserSession({ cookies: [liAt] });
    expect(await sessionDetector.hasValidSession(valid)).toBe(true);

    const expired = new FakeBrowserSession({
      cookies: [liAt],
      redirects: { [LinkedInSelectors.feedUrl]: "https://www.linkedin.com/login" },
    });
    expect(await sessionDetector.hasValidSession(expired)).toBe(false);
  });
});

describe("LinkedInIdentityReader", () => {
  const { identityReader } = createLinkedInPortal();

  it("usa la API interna con el CSRF de JSESSIONID", async () => {
    const session = new FakeBrowserSession({
      cookies: [liAt, { name: "JSESSIONID", value: '"ajax:123"' }],
      api: { [LinkedInSelectors.meEndpoint]: { data: { "*miniProfile": "urn:li:fs_miniProfile:ACoAAB1234567890" } } },
    });
    expect((await identityReader.read(session))?.memberUrn).toBe("urn:li:fsd_profile:ACoAAB1234567890");
  });

  it("si la API falla, lee el HTML de /in/me/", async () => {
    const session = new FakeBrowserSession({
      cookies: [liAt],
      redirects: { [LinkedInSelectors.ownProfileUrl]: "https://www.linkedin.com/in/ana-perez/" },
      pages: { "https://www.linkedin.com/in/ana-perez/": "<html>urn:li:fsd_profile:ACoAAB1234567890</html>" },
      texts: { [LinkedInSelectors.profileNameHeading]: " Ana María Pérez " },
    });
    expect(await identityReader.read(session)).toMatchObject({
      memberUrn: "urn:li:fsd_profile:ACoAAB1234567890",
      basics: { firstName: "Ana", lastName: "María Pérez", publicProfileUrl: "https://www.linkedin.com/in/ana-perez/" },
    });
  });
});

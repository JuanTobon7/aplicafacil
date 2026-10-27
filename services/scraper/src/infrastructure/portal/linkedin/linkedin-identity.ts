/** Tipos de contracts/openapi/scraper-browser.yaml (mismos que linkedin-contract en Java). */

export type ProfileBasics = {
  firstName: string | null;
  lastName: string | null;
  headline: string | null;
  photoUrl: string | null;
  publicProfileUrl: string | null;
  location: string | null;
};

export type LinkedInIdentity = {
  source: "SESSION";
  memberUrn: string;
  oidcSub: null;
  email: string | null;
  emailVerified: boolean;
  basics: ProfileBasics;
};

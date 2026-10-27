import type { JobPortal } from "../../../application/port/out/job-portal";
import type { LinkedInIdentity } from "./linkedin-identity";
import { LinkedInIdentityReader } from "./linkedin-identity-reader";
import { LinkedInProfileReader } from "./linkedin-profile-reader";
import { LinkedInSessionDetector } from "./linkedin-session-detector";
import { LinkedInSelectors } from "./linkedin.selectors";

export const LINKEDIN_PORTAL_ID = "linkedin";

/** Composición del portal LinkedIn. Un portal nuevo (Computrabajo, …) es otra función como esta. */
export function createLinkedInPortal(): JobPortal<LinkedInIdentity> {
  return {
    id: LINKEDIN_PORTAL_ID,
    displayName: "LinkedIn",
    loginUrl: LinkedInSelectors.loginUrl,
    sessionDetector: new LinkedInSessionDetector(),
    identityReader: new LinkedInIdentityReader(),
    profileReader: new LinkedInProfileReader(),
  };
}

import type { LinkedInIdentity, ProfileBasics } from "./linkedin-identity";
import { memberUrn, publicProfileUrl } from "./linkedin.selectors";

type Json = Record<string, any>;

/** Respuesta normalizada de /voyager/api/me: data.*miniProfile apunta a una entrada de included. */
export function parseMe(root: Json): LinkedInIdentity | null {
  const miniProfileUrn: string | undefined = root?.data?.["*miniProfile"];
  if (!miniProfileUrn) return null;
  const profile: Json | undefined = (root.included ?? []).find((i: Json) => i?.entityUrn === miniProfileUrn);
  const memberId = miniProfileUrn.substring(miniProfileUrn.lastIndexOf(":") + 1);
  return linkedInIdentity(memberId, basicsOf(profile));
}

function basicsOf(profile: Json | undefined): ProfileBasics {
  return {
    firstName: text(profile?.firstName),
    lastName: text(profile?.lastName),
    headline: text(profile?.occupation),
    photoUrl: photoUrl(profile?.picture),
    publicProfileUrl: profile?.publicIdentifier ? publicProfileUrl(profile.publicIdentifier) : null,
    location: null,
  };
}

/**
 * El correo se lee con el perfil completo (etapa 3, información de contacto). Un correo leído de la página no
 * se considera verificado: solo un correo verificado vincula cuentas entre modos.
 */
export function linkedInIdentity(memberId: string, basics: ProfileBasics): LinkedInIdentity {
  return { source: "SESSION", memberUrn: memberUrn(memberId), oidcSub: null, email: null, emailVerified: false, basics };
}

/** La foto más grande disponible: rootUrl + último artifact. */
function photoUrl(picture: Json | undefined): string | null {
  const vector = picture?.["com.linkedin.common.VectorImage"] ?? picture;
  const root = text(vector?.rootUrl);
  const artifacts: Json[] = vector?.artifacts ?? [];
  const segment = text(artifacts.at(-1)?.fileIdentifyingUrlPathSegment);
  return root && segment ? root + segment : null;
}

function text(value: unknown): string | null {
  return typeof value === "string" && value.trim() ? value : null;
}

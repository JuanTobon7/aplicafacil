import type { PortalIdentityReader } from "./portal-identity-reader";
import type { PortalProfileReader } from "./portal-profile-reader";
import type { PortalSessionDetector } from "./portal-session-detector";

/**
 * Un portal de empleo (LinkedIn, Computrabajo, …). Los servicios de aplicación solo conocen esta abstracción, así que
 * cualquier implementación debe poder sustituir a otra sin cambiarlos (Liskov). Lo verifica la suite de contrato
 * `test/infrastructure/portal/job-portal.contract.ts`, que todo portal nuevo debe pasar.
 */
export interface JobPortal<TIdentity extends object = object> {
  /** Identificador en la URL: /portals/{id}/sessions/... */
  readonly id: string;
  /** Nombre para los mensajes al usuario. */
  readonly displayName: string;
  /** Página oficial de inicio de sesión (https). */
  readonly loginUrl: string;
  readonly sessionDetector: PortalSessionDetector;
  readonly identityReader: PortalIdentityReader<TIdentity>;
  readonly profileReader: PortalProfileReader;
}

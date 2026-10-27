import type { ConnectStep } from "../../../domain/connect-step";
import type { ProfileSnapshot } from "../../../domain/profile-snapshot";
import type { StorageState } from "../../../domain/storage-state";
import type { JobPortal } from "../out/job-portal";

export type LoginOptions = { loginUrl?: string; timeoutMs: number };

export type LoginResult<TIdentity extends object = object> = {
  identity: TIdentity;
  profile: ProfileSnapshot;
  storageState: StorageState;
};

/** Inicio de sesión manual y visible en un portal. Un intento a la vez en todo el proceso. */
export interface PortalLoginService {
  /** Reserva la ventana visible; lanza ALREADY_CONNECTING o BROWSER_NOT_READY. */
  reserve(attemptId: string): void;
  cancel(attemptId: string): void;
  execute<TIdentity extends object>(
    portal: JobPortal<TIdentity>,
    attemptId: string,
    options: LoginOptions,
    onStep: (step: ConnectStep) => void,
  ): Promise<LoginResult<TIdentity>>;
}

export const PORTAL_LOGIN_SERVICE = Symbol("PortalLoginService");

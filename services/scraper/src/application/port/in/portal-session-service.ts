import type { ProfileSnapshot } from "../../../domain/profile-snapshot";
import type { StorageState } from "../../../domain/storage-state";
import type { JobPortal } from "../out/job-portal";

export type SessionResult = { valid: boolean; storageState?: StorageState };

/** Operaciones headless con una sesión guardada; devuelven el storageState renovado. */
export interface PortalSessionService {
  validate(portal: JobPortal, storageState: StorageState): Promise<SessionResult>;
  readProfile(portal: JobPortal, storageState: StorageState): Promise<SessionResult & { profile?: ProfileSnapshot }>;
}

export const PORTAL_SESSION_SERVICE = Symbol("PortalSessionService");

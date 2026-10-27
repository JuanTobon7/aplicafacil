import type { JobPortal } from "./job-portal";

export interface PortalRegistry {
  /** Lanza ScraperError(UNKNOWN_PORTAL) si no existe. */
  get(id: string): JobPortal;
  list(): readonly JobPortal[];
}

export const PORTAL_REGISTRY = Symbol("PortalRegistry");
/** Lista de portales registrados en la composición. */
export const JOB_PORTALS = Symbol("JobPortals");

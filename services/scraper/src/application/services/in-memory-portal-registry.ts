import { ScraperError } from "../exception/scraper-error";
import type { JobPortal } from "../port/out/job-portal";
import type { PortalRegistry } from "../port/out/portal-registry";

export class InMemoryPortalRegistry implements PortalRegistry {
  private readonly byId = new Map<string, JobPortal>();

  constructor(portals: readonly JobPortal[]) {
    for (const portal of portals) {
      if (this.byId.has(portal.id)) throw new Error(`Portal duplicado: ${portal.id}`);
      this.byId.set(portal.id, portal);
    }
  }

  get(id: string): JobPortal {
    const portal = this.byId.get(id);
    if (!portal) throw new ScraperError("UNKNOWN_PORTAL", `Portal no soportado: ${id}`);
    return portal;
  }

  list(): readonly JobPortal[] {
    return [...this.byId.values()];
  }
}

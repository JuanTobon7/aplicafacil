import { ScraperError } from "../exception/scraper-error";
import type { JobSearchPortal, JobSearchPortalRegistry } from "../port/out/job-search-portal";

export class InMemoryJobSearchPortalRegistry implements JobSearchPortalRegistry {
  private readonly byId = new Map<string, JobSearchPortal>();

  constructor(portals: readonly JobSearchPortal[]) {
    for (const portal of portals) {
      if (this.byId.has(portal.id)) throw new Error(`Portal de búsqueda duplicado: ${portal.id}`);
      this.byId.set(portal.id, portal);
    }
  }

  get(id: string): JobSearchPortal {
    const portal = this.byId.get(id);
    if (!portal) throw new ScraperError("UNKNOWN_PORTAL", `Búsqueda no soportada en el portal: ${id}`);
    return portal;
  }

  list(): readonly JobSearchPortal[] {
    return [...this.byId.values()];
  }
}

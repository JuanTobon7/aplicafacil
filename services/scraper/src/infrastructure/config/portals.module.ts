import { Module } from "@nestjs/common";
import type { JobPortal } from "../../application/port/out/job-portal";
import {
  JOB_SEARCH_PORTAL_REGISTRY,
  JOB_SEARCH_PORTALS,
  type JobSearchPortal,
} from "../../application/port/out/job-search-portal";
import { JOB_PORTALS, PORTAL_REGISTRY } from "../../application/port/out/portal-registry";
import { InMemoryJobSearchPortalRegistry } from "../../application/services/in-memory-job-search-portal-registry";
import { InMemoryPortalRegistry } from "../../application/services/in-memory-portal-registry";
import { ComputrabajoJobSearch } from "../portal/computrabajo/computrabajo-job-search";
import { createComputrabajoPortal } from "../portal/computrabajo/computrabajo.portal";
import { LinkedInJobSearch } from "../portal/linkedin/linkedin-job-search";
import { createLinkedInPortal } from "../portal/linkedin/linkedin.portal";
import { scraperProperties } from "./scraper-properties";

/**
 * Portales disponibles.
 * - Sesión (login, identidad, perfil): `JOB_PORTALS`.
 * - Búsqueda de empleo: `JOB_SEARCH_PORTALS`. Un portal nuevo = una clase `JobSearchPortal` más en la lista.
 */
@Module({
  providers: [
    {
      provide: JOB_PORTALS,
      useFactory: (): JobPortal[] => [createLinkedInPortal(), createComputrabajoPortal(scraperProperties.computrabajoCountry)],
    },
    {
      provide: PORTAL_REGISTRY,
      useFactory: (portals: JobPortal[]) => new InMemoryPortalRegistry(portals),
      inject: [JOB_PORTALS],
    },
    {
      provide: JOB_SEARCH_PORTALS,
      useFactory: (): JobSearchPortal[] => [
        new LinkedInJobSearch(),
        new ComputrabajoJobSearch(scraperProperties.computrabajoCountry),
      ],
    },
    {
      provide: JOB_SEARCH_PORTAL_REGISTRY,
      useFactory: (portals: JobSearchPortal[]) => new InMemoryJobSearchPortalRegistry(portals),
      inject: [JOB_SEARCH_PORTALS],
    },
  ],
  exports: [PORTAL_REGISTRY, JOB_SEARCH_PORTAL_REGISTRY],
})
export class PortalsModule {}

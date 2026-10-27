import { Body, Controller, HttpCode, Inject, Param, Post } from "@nestjs/common";
import { JOB_SEARCH_SERVICE, normalizeCriteria, type JobSearchService } from "../application/port/in/job-search-service";
import type { JobSearchCriteria } from "../domain/job-card";
import type { StorageState } from "../domain/storage-state";
import { scraperProperties } from "../infrastructure/config/scraper-properties";

type SearchRequest = { criteria?: Partial<JobSearchCriteria>; storageState?: StorageState };

/**
 * POST /portals/{portal}/jobs/search (contracts/openapi/scraper-browser.yaml). Responde con las ofertas y, si se usó
 * una sesión, el storageState renovado. Nunca se loguea el storageState.
 */
@Controller("portals/:portal/jobs")
export class JobSearchController {
  constructor(@Inject(JOB_SEARCH_SERVICE) private readonly jobs: JobSearchService) {}

  @Post("search")
  @HttpCode(200)
  search(@Param("portal") portal: string, @Body() body: SearchRequest | undefined) {
    const criteria = normalizeCriteria(body?.criteria ?? {}, scraperProperties.maxSearchResults);
    const storageState = body?.storageState && typeof body.storageState === "object" ? body.storageState : undefined;
    return this.jobs.search(portal, criteria, storageState);
  }
}

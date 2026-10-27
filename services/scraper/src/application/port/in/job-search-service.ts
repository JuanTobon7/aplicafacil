import type { JobCard, JobSearchCriteria } from "../../../domain/job-card";
import type { StorageState } from "../../../domain/storage-state";
import { ScraperError } from "../../exception/scraper-error";

export type JobSearchResult = {
  /** false: el portal exige sesión y la recibida venció (no se buscó). */
  valid: boolean;
  /** Renovado; solo si se usó una sesión y sigue válida. */
  storageState?: StorageState;
  jobs: JobCard[];
  pagesVisited: number;
};

/** Busca ofertas en un portal y recorre sus cards. Headless, contexto NO persistente. */
export interface JobSearchService {
  search(portalId: string, criteria: JobSearchCriteria, storageState?: StorageState): Promise<JobSearchResult>;
}

export const JOB_SEARCH_SERVICE = Symbol("JobSearchService");

/** Valida y acota los criterios que llegan por HTTP. */
export function normalizeCriteria(input: Partial<JobSearchCriteria>, maxAllowed: number): JobSearchCriteria {
  const keywords = typeof input.keywords === "string" ? input.keywords.trim() : "";
  if (keywords.length < 2 || keywords.length > 200) {
    throw new ScraperError("INVALID_REQUEST", "keywords debe tener entre 2 y 200 caracteres.");
  }
  const maxResults = Math.min(Math.max(Math.trunc(Number(input.maxResults) || 25), 1), maxAllowed);
  const location = typeof input.location === "string" && input.location.trim() ? input.location.trim().slice(0, 120) : undefined;
  return { keywords, location, maxResults, ...filters(input) };
}

/** Fecha (1..30 días) y modalidad: solo se incluyen si vienen bien formados. */
function filters(input: Partial<JobSearchCriteria>): Pick<JobSearchCriteria, "postedWithinDays" | "remote"> {
  const days = Number(input.postedWithinDays);
  return {
    ...(Number.isFinite(days) && days >= 1 && days <= 30 ? { postedWithinDays: Math.trunc(days) } : {}),
    ...(input.remote === true ? { remote: true } : {}),
  };
}

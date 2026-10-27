import type { JobCard, JobSearchCriteria } from "../../../domain/job-card";
import { jobTitle } from "../../../domain/job-title";
import type { ExtractedRecord } from "../../../application/port/out/browser-page";
import type { JobSearchPortal } from "../../../application/port/out/job-search-portal";
import { ComputrabajoSelectors, pubdateFor, slug } from "./computrabajo.selectors";

export const COMPUTRABAJO_PORTAL_ID = "computrabajo";

/**
 * Búsqueda pública en Computrabajo (sin sesión): /trabajo-de-{palabras}[-en-{lugar} | -en-remoto]?pubdate={días}&p={página}.
 * Una oferta remota no tiene lugar: con `remote` se ignora `location`.
 */
export class ComputrabajoJobSearch implements JobSearchPortal {
  readonly id = COMPUTRABAJO_PORTAL_ID;
  readonly displayName = "Computrabajo";
  readonly requiresSession = false;
  readonly pageSize = ComputrabajoSelectors.pageSize;
  private readonly origin: string;

  constructor(country = "co") {
    if (!/^[a-z]{2}$/.test(country)) throw new Error(`País de Computrabajo inválido: ${country}`);
    this.origin = ComputrabajoSelectors.origin(country);
  }

  readonly cards = {
    readyWhen: ComputrabajoSelectors.card,
    emptyWhen: ComputrabajoSelectors.noResults,
    item: ComputrabajoSelectors.card,
    fields: {
      id: { attribute: ComputrabajoSelectors.cardIdAttribute },
      title: { selector: ComputrabajoSelectors.title },
      href: { selector: ComputrabajoSelectors.title, attribute: "href" },
      company: { selector: ComputrabajoSelectors.company },
      location: { selector: ComputrabajoSelectors.location },
      posted: { selector: ComputrabajoSelectors.posted },
      salary: { selector: ComputrabajoSelectors.salary },
    },
  };

  searchUrl(criteria: JobSearchCriteria, pageIndex: number): string {
    const what = slug(criteria.keywords);
    const where = criteria.remote ? ComputrabajoSelectors.remoteSuffix : criteria.location ? `-en-${slug(criteria.location)}` : "";
    const url = new URL(`/trabajo-de-${what}${where}`, this.origin);
    const pubdate = pubdateFor(criteria.postedWithinDays);
    if (pubdate) url.searchParams.set("pubdate", String(pubdate));
    if (pageIndex > 0) url.searchParams.set("p", String(pageIndex + 1));
    return url.toString();
  }

  toCard(record: ExtractedRecord): JobCard | null {
    if (!record.id || !record.title || !record.href) return null;
    let url: URL;
    try {
      url = new URL(record.href, this.origin);
    } catch {
      return null;
    }
    if (url.origin !== this.origin) return null; // publicidad o enlaces externos
    url.hash = "";
    return {
      externalId: record.id,
      url: url.toString(),
      title: jobTitle(record.title),
      // Algunas ofertas son de empresas confidenciales
      company: record.company ?? "Empresa confidencial",
      location: record.location,
      postedText: record.posted,
      salaryText: record.salary,
      // El listado no marca la postulación rápida: se informa false en vez de adivinar
      quickApply: false,
    };
  }
}

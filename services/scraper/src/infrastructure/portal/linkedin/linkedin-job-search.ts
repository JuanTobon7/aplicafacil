import type { JobCard, JobSearchCriteria } from "../../../domain/job-card";
import { jobTitle } from "../../../domain/job-title";
import type { ExtractedRecord } from "../../../application/port/out/browser-page";
import type { JobSearchPortal } from "../../../application/port/out/job-search-portal";
import { LinkedInJobSelectors, LinkedInSelectors } from "./linkedin.selectors";
import { LINKEDIN_PORTAL_ID } from "./linkedin.portal";

/**
 * Búsqueda de empleo en LinkedIn con la sesión del usuario (el listado completo y Easy Apply solo se ven
 * con sesión iniciada). LinkedIn carga las cards al desplazar la lista: la fachada hace scroll en `scrollContainer`.
 */
export class LinkedInJobSearch implements JobSearchPortal {
  readonly id = LINKEDIN_PORTAL_ID;
  readonly displayName = "LinkedIn";
  readonly requiresSession = true;
  readonly pageSize = LinkedInJobSelectors.pageSize;

  readonly cards = {
    readyWhen: LinkedInJobSelectors.card,
    emptyWhen: LinkedInJobSelectors.noResults,
    item: LinkedInJobSelectors.card,
    fields: {
      componentKey: { attribute: LinkedInJobSelectors.cardIdAttribute },
      title: { selector: LinkedInJobSelectors.title },
      company: { selector: LinkedInJobSelectors.company },
      location: { selector: LinkedInJobSelectors.location },
      /** Todo el texto de la card: de ahí salen la fecha y "Solicitud sencilla". */
      text: {},
    },
    /** El título: abre el detalle en el panel derecho del listado. */
    openTarget: LinkedInJobSelectors.title,
    /** Con la ventana angosta el detalle tapa el listado: su flecha "←" vuelve a él. */
    closeTarget: LinkedInJobSelectors.backToResults,
  };

  searchUrl(criteria: JobSearchCriteria, pageIndex: number): string {
    const url = new URL(LinkedInJobSelectors.searchUrl);
    url.searchParams.set("keywords", criteria.keywords);
    if (criteria.location) url.searchParams.set("location", criteria.location);
    if (criteria.postedWithinDays) url.searchParams.set("f_TPR", `r${criteria.postedWithinDays * 86_400}`);
    if (criteria.remote) url.searchParams.set("f_WT", LinkedInJobSelectors.remoteWorkplaceType);
    if (pageIndex > 0) url.searchParams.set("start", String(pageIndex * this.pageSize));
    return url.toString();
  }

  toCard(record: ExtractedRecord): JobCard | null {
    const externalId = record.componentKey?.match(LinkedInJobSelectors.cardIdPattern)?.[1];
    // Las cards perezosas que aún no cargaron solo tienen el id: se descartan
    if (!externalId || !record.title || !record.company) return null;
    const text = record.text ?? "";
    return {
      externalId,
      url: `${LinkedInSelectors.origin}/jobs/view/${externalId}/`,
      title: cleanTitle(record.title),
      company: record.company,
      location: record.location,
      postedText: text.match(LinkedInJobSelectors.postedPattern)?.[1] ?? null,
      salaryText: null,
      quickApply: LinkedInJobSelectors.easyApplyLabels.some((label) => text.toLowerCase().includes(label)),
    };
  }
}

/**
 * LinkedIn agrega al título la insignia de empresa verificada ("with verification" / "con verificación"). Lo demás
 * (el título repetido para lectores de pantalla) lo resuelve la regla de dominio {@link jobTitle}.
 */
function cleanTitle(title: string): string {
  return jobTitle(title.replace(/\s*(with verification|con verificación)\s*$/i, ""));
}

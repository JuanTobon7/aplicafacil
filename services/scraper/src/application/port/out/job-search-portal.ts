import type { JobCard, JobSearchCriteria } from "../../../domain/job-card";
import type { ExtractedRecord, FieldSpec } from "./browser-page";

/**
 * Estrategia de búsqueda de un portal (LinkedIn, Computrabajo, …): solo lo que depende del sitio (URLs, selectores
 * y cómo leer una card). Los pasos (abrir el navegador, validar la sesión, esperar, desplazar, recorrer las cards,
 * paginar, deduplicar) los ejecuta `JobSearchFacade`, igual para todos.
 *
 * Contrato (Liskov), verificado por `test/infrastructure/portal/job-search-portal.contract.ts`:
 * - `searchUrl` es https del propio portal y cambia con la página.
 * - `toCard` devuelve null para una card incompleta o de publicidad, nunca lanza.
 *
 * Está separada de `JobPortal` (sesión, identidad, perfil) porque hay portales donde se busca sin iniciar sesión.
 */
export interface JobSearchPortal {
  /** Mismo id que en la URL: /portals/{id}/jobs/search */
  readonly id: string;
  readonly displayName: string;
  /** true: la búsqueda necesita la sesión guardada del usuario (storageState). */
  readonly requiresSession: boolean;
  /** Ofertas que muestra cada página del listado: si una página trae menos, era la última. */
  readonly pageSize: number;

  /** URL del listado para la página `pageIndex` (0, 1, 2…). */
  searchUrl(criteria: JobSearchCriteria, pageIndex: number): string;

  /** Cómo encontrar y leer las cards del listado. */
  readonly cards: {
    /** Aparece cuando el listado ya cargó. */
    readyWhen: string;
    /** Algo que solo aparece cuando la búsqueda no tiene resultados. */
    emptyWhen?: string;
    /** Contenedor con scroll propio (LinkedIn carga las cards al desplazar la lista). */
    scrollContainer?: string;
    /** Una card. */
    item: string;
    fields: Record<string, FieldSpec>;
    /** Qué clic abre el detalle de una card sin salir del listado (solo se usa al grabar). */
    openTarget?: string;
    /** Qué clic vuelve al listado cuando el detalle lo tapa (ventana angosta; solo se usa al grabar). */
    closeTarget?: string;
  };

  /** Convierte lo leído de una card en una oferta normalizada, o null si no sirve. */
  toCard(record: ExtractedRecord): JobCard | null;
}

export interface JobSearchPortalRegistry {
  /** Lanza ScraperError(UNKNOWN_PORTAL) si no existe. */
  get(id: string): JobSearchPortal;
  list(): readonly JobSearchPortal[];
}

export const JOB_SEARCH_PORTAL_REGISTRY = Symbol("JobSearchPortalRegistry");
export const JOB_SEARCH_PORTALS = Symbol("JobSearchPortals");

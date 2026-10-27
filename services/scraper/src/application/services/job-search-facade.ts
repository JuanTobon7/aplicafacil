import type { JobCard, JobSearchCriteria } from "../../domain/job-card";
import { InvalidJobCardError } from "../../domain/job-title";
import type { StorageState } from "../../domain/storage-state";
import { ScraperError } from "../exception/scraper-error";
import type { JobSearchResult, JobSearchService } from "../port/in/job-search-service";
import type { AppLogger } from "../port/out/app-logger";
import type { BrowserPage, ExtractedRecord } from "../port/out/browser-page";
import type { BrowserSessionFactory } from "../port/out/browser-session-factory";
import type { Clock } from "../port/out/clock";
import type { JobSearchPortal, JobSearchPortalRegistry } from "../port/out/job-search-portal";
import type { PortalRegistry } from "../port/out/portal-registry";
import { withPage } from "../port/out/with-page";

export type JobSearchLimits = {
  /** Páginas máximas por búsqueda, aunque falten resultados. */
  maxPages: number;
  /** Pausa entre páginas (ms): [mínimo, máximo] al azar, para no parecer un robot ni saturar el portal. */
  pauseBetweenPagesMs: [number, number];
  /** Cuánto esperar a que cargue un listado. */
  listTimeoutMs: number;
  /** true: la búsqueda se hace en una ventana visible para ver lo que hace el navegador (SCRAPER_SHOW_BROWSER). */
  showBrowser?: boolean;
  /** Carpeta donde se graba un video de cada búsqueda (SCRAPER_RECORD_DIR); sin ella no se graba. */
  recordDir?: string;
  /** Al grabar: cuánto se muestra cada card (resaltada y abierta) para que se vea el recorrido. */
  showcaseCardMs?: number;
};

export const DEFAULT_LIMITS: JobSearchLimits = { maxPages: 10, pauseBetweenPagesMs: [1500, 3500], listTimeoutMs: 20_000 };

/**
 * Fachada (Facade) de la búsqueda de empleo: una sola llamada esconde los subsistemas del navegador.
 *
 *   1. elige la estrategia del portal (`JobSearchPortal`)
 *   2. abre un contexto NO persistente (headless, o visible con SCRAPER_SHOW_BROWSER) (con el storageState si el portal exige sesión)
 *   3. valida la sesión con el detector del portal (si vencio: `valid=false`, sin buscar)
 *   4. por cada página: navega, espera el listado, desplaza para cargar las cards perezosas y las recorre
 *   5. normaliza y deduplica por `externalId` hasta `maxResults`
 *   6. exporta el storageState renovado y cierra el contexto siempre
 *
 * Los portales solo aportan URLs, selectores y el mapeo de una card; esta clase no conoce ningún sitio.
 */
/** Lo que la fachada necesita, con nombre (un objeto en vez de siete parámetros posicionales). */
export type JobSearchFacadeDeps = {
  searchPortals: JobSearchPortalRegistry;
  portals: PortalRegistry;
  sessions: BrowserSessionFactory;
  clock: Clock;
  logger: AppLogger;
  limits?: JobSearchLimits;
  /** Inyectable para que las pausas sean deterministas en las pruebas. */
  random?: () => number;
};

export class JobSearchFacade implements JobSearchService {
  private readonly searchPortals: JobSearchPortalRegistry;
  private readonly portals: PortalRegistry;
  private readonly sessions: BrowserSessionFactory;
  private readonly clock: Clock;
  private readonly logger: AppLogger;
  private readonly limits: JobSearchLimits;
  private readonly random: () => number;

  constructor(deps: JobSearchFacadeDeps) {
    this.searchPortals = deps.searchPortals;
    this.portals = deps.portals;
    this.sessions = deps.sessions;
    this.clock = deps.clock;
    this.logger = deps.logger;
    this.limits = deps.limits ?? DEFAULT_LIMITS;
    this.random = deps.random ?? Math.random;
  }

  async search(portalId: string, criteria: JobSearchCriteria, storageState?: StorageState): Promise<JobSearchResult> {
    const portal = this.searchPortals.get(portalId);
    if (portal.requiresSession && !storageState) {
      throw new ScraperError("SESSION_REQUIRED", `${portal.displayName} exige una sesión iniciada para buscar.`);
    }
    const session = await this.open(portal, criteria, storageState);
    try {
      if (portal.requiresSession) {
        this.logger.log(`[${portal.id}] Verificando que la sesión siga iniciada`);
        if (!(await this.portals.get(portal.id).sessionDetector.hasValidSession(session))) {
          this.logger.warn(`[${portal.id}] La sesión venció: no se busca`);
          return { valid: false, jobs: [], pagesVisited: 0 };
        }
      }
      const { jobs, pagesVisited } = await withPage(session, (page) => this.collect(portal, page, criteria));
      this.logger.log(`Búsqueda en ${portal.id}: ${jobs.length} ofertas en ${pagesVisited} páginas`);
      return {
        valid: true,
        jobs,
        pagesVisited,
        ...(portal.requiresSession ? { storageState: await session.exportState() } : {}),
      };
    } finally {
      await session.close().catch(() => undefined);
    }
  }

  /** Abre el contexto (visible y grabado según los límites) con la sesión si el portal la exige. */
  private open(portal: JobSearchPortal, criteria: JobSearchCriteria, storageState?: StorageState) {
    const visible = this.limits.showBrowser === true;
    this.logger.log(
      `[${portal.id}] Abriendo navegador ${visible ? "visible" : "sin ventana"} para "${criteria.keywords}"` +
        `${criteria.location ? ` en ${criteria.location}` : ""} (máx. ${criteria.maxResults} ofertas)`,
    );
    const recordVideoDir = this.limits.recordDir;
    if (recordVideoDir) this.logger.log(`[${portal.id}] Grabando la búsqueda en ${recordVideoDir}`);
    return this.sessions.openSession({
      visible,
      storageState: portal.requiresSession ? storageState : undefined,
      ...(recordVideoDir && { recordVideoDir }),
    });
  }

  /** Recorre página por página las cards del listado (iteración paginada). */
  private async collect(portal: JobSearchPortal, page: BrowserPage, criteria: JobSearchCriteria) {
    const found = new Map<string, JobCard>();
    let pagesVisited = 0;
    for (let index = 0; index < this.limits.maxPages && found.size < criteria.maxResults; index++) {
      if (index > 0) {
        const pause = this.pause();
        this.logger.log(`[${portal.id}] Pausa de ${(pause / 1000).toFixed(1)} s antes de la página ${index + 1}`);
        await this.clock.sleep(pause);
      }
      const cards = await this.readPage(portal, page, criteria, index);
      pagesVisited++;
      const before = found.size;
      for (const card of cards) {
        if (found.size >= criteria.maxResults) break;
        if (!found.has(card.externalId)) found.set(card.externalId, card);
      }
      this.logger.log(
        `[${portal.id}] Página ${index + 1}: ${cards.length} cards leídas, ${found.size - before} ofertas nuevas en esta búsqueda`,
      );
      // Última página: trae menos de lo normal o solo repetidas (algunos portales repiten la última página)
      if (cards.length < portal.pageSize || found.size === before) break;
    }
    return { jobs: [...found.values()], pagesVisited };
  }

  private async readPage(portal: JobSearchPortal, page: BrowserPage, criteria: JobSearchCriteria, index: number) {
    const url = portal.searchUrl(criteria, index);
    this.logger.log(`[${portal.id}] Página ${index + 1}: abriendo ${url}`);
    try {
      await page.goto(url, { waitUntil: "domcontentloaded", timeoutMs: 45_000 });
    } catch (error) {
      // net::ERR_NAME_NOT_RESOLVED, timeout… El motor no se nombra: solo la primera línea del mensaje
      const reason = (error instanceof Error ? error.message : String(error)).split("\n")[0].replace(/^page\.goto:\s*/, "");
      throw new ScraperError("PORTAL_UNREACHABLE", `No se pudo abrir ${portal.displayName}: ${reason}`);
    }
    const listed = await page.waitFor(portal.cards.readyWhen, this.limits.listTimeoutMs);
    if (!listed) {
      if (portal.cards.emptyWhen && (await page.textOf(portal.cards.emptyWhen)) !== null) {
        this.logger.log(`[${portal.id}] Página ${index + 1}: el portal dice que no hay resultados`);
        return [];
      }
      if (index === 0) {
        throw new ScraperError("SEARCH_FAILED", `${portal.displayName} no mostró el listado de ofertas (¿cambió el sitio?).`);
      }
      return [];
    }
    this.logger.log(`[${portal.id}] Página ${index + 1}: listado cargado; desplazando para cargar todas las cards`);
    await page.scrollToEnd(portal.cards.scrollContainer);
    const records = await page.extractAll(portal.cards.item, portal.cards.fields);
    await this.showcase(portal, page, records);
    return records.map((r) => this.toCard(portal, r)).filter((c): c is JobCard => c !== null);
  }

  /** Solo al grabar: recorre las cards una a una a la vista (la lectura ya se hizo; esto es para el video). */
  private async showcase(portal: JobSearchPortal, page: BrowserPage, records: ExtractedRecord[]) {
    const holdMs = this.limits.showcaseCardMs;
    if (!holdMs) return;
    for (const [index, record] of records.entries()) {
      this.logger.log(`[${portal.id}] Card ${index + 1}/${records.length}: ${record.title ?? "(sin título)"}`);
      await page.spotlight(portal.cards.item, index, {
        open: portal.cards.openTarget,
        close: portal.cards.closeTarget,
        holdMs,
      });
    }
  }

  /** Una card que no cumple las reglas de dominio se descarta (con aviso); el resto de la página sigue. */
  private toCard(portal: JobSearchPortal, record: ExtractedRecord): JobCard | null {
    try {
      return portal.toCard(record);
    } catch (e) {
      if (!(e instanceof InvalidJobCardError)) throw e;
      this.logger.warn(`[${portal.id}] Card descartada: ${e.message}`);
      return null;
    }
  }

  private pause(): number {
    const [min, max] = this.limits.pauseBetweenPagesMs;
    return Math.round(min + (max - min) * this.random());
  }
}

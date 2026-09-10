import { Injectable, Logger } from '@nestjs/common';
import { Page } from 'puppeteer';
import { LinkedInSearchParams } from '../../../dto/params.lindkln.search';
import { JobSearchComponent } from '../contract/job.search.component';

/**
 * Selectores estables del DOM de LinkedIn (variante scaffold-layout).
 *
 * LinkedIn genera clases CSS dinámicas (hashes tipo `_970c3eec`,
 * `yACmkvJPKuTKPJgaDkPBuzoDHPNuZJKI`, etc.) que cambian en cada deploy.
 * En su lugar usamos clases semánticas estables y atributos de enlace:
 *
 * scaffold-layout__list
 *         │
 *         ▼
 * .job-card-container
 *         │
 *         ▼
 * a[href*="/jobs/view/"]
 *         │
 *         ▼
 * href → URL absoluta de la vacante
 *
 * La URL se extrae del href del enlace de cada tarjeta (NO se reconstruye
 * manualmente a partir del título o del jobId).
 */
const SELECTORS = {
  /** Contenedor principal de la lista de resultados de búsqueda. */
  RESULTS_CONTAINER: '.scaffold-layout__list',
  /** Cada tarjeta de empleo dentro del contenedor de resultados. */
  JOB_CARD: '.job-card-container',
  /** Enlace al detalle de la vacante dentro de cada tarjeta. */
  JOB_URL: 'a[href*="/jobs/view/"]',
} as const;

/** Prefijo base para convertir URLs relativas de LinkedIn en absolutas. */
const LINKEDIN_BASE_URL = 'https://www.linkedin.com';

@Injectable()
export class JobSearchComponentImpl implements JobSearchComponent {
  private readonly logger = new Logger(JobSearchComponentImpl.name);

  private readonly JOBS_URL = 'https://www.linkedin.com/jobs/search/';

  private readonly TIME_FILTER_MAP: Record<string, string> = {
    '24_hour': 'r86400',
    '1_week': 'r604800',
    '1_month': 'r2592000',
    any_time: '',
  };

  async searchJobs(
    page: Page,
    params: LinkedInSearchParams,
  ): Promise<string[]> {
    this.logger.log('Searching jobs...');

    const timeFilter = this.TIME_FILTER_MAP[params.timeFilter] ?? '';
    const remoteParam = params.remote ? 'f_WT=2' : '';
    const locationParam = encodeURIComponent(params.location);

    const url = `${this.JOBS_URL}?keywords=${encodeURIComponent(params.title)}&location=${locationParam}&${remoteParam}&f_TPR=${timeFilter}&f_AL=${params.easyApply}`;

    // NOTA: usamos 'domcontentloaded' en lugar de 'networkidle2' porque
    // LinkedIn mantiene conexiones persistentes (websockets/polling) que
    // impiden que la red alcance 'idle', colgando el page.goto.
    await page.goto(url, {
      waitUntil: 'domcontentloaded',
      timeout: 60_000,
    });

    // Esperar a que cargue la lista de resultados
    await page.waitForSelector(SELECTORS.JOB_CARD, {
      timeout: 30_000,
    });

    // Scroll para cargar más resultados
    await this.autoScroll(page);

    // Extraer las URLs de las tarjetas de empleo
    const jobLinks = await this.collectJobLinks(page);

    this.logger.log(`Found ${jobLinks.length} job links.`);
    return jobLinks;
  }

  /**
   * Hace click en la tarjeta de empleo correspondiente al jobId para
   * cargar su detalle en el panel derecho (navegación entre tarjetas).
   */
  async clickJobCard(page: Page, jobId: string): Promise<void> {
    const card = await page.$(
      `${SELECTORS.RESULTS_CONTAINER} ${SELECTORS.JOB_CARD} ${SELECTORS.JOB_URL}[href*="/jobs/view/${jobId}"]`,
    );

    if (!card) {
      throw new Error(`Job card ${jobId} not found in the results list.`);
    }

    // Click en el enlace del título (evita abrir pestaña nueva)
    await card.click();
  }

  /**
   * Itera sobre las tarjetas de empleo dentro del contenedor de resultados.
   *
   * Por cada tarjeta:
   * 1. Busca el enlace `a[href*="/jobs/view/"]`.
   * 2. Extrae el atributo `href`.
   * 3. Convierte la URL relativa en absoluta cuando es necesario.
   */
  private async collectJobLinks(page: Page): Promise<string[]> {
    const jobLinks: string[] = [];

    const cards = await page.$$(
      `${SELECTORS.RESULTS_CONTAINER} ${SELECTORS.JOB_CARD}`,
    );

    this.logger.log(`Found ${cards.length} job cards in the results list.`);

    for (const card of cards) {
      const link = await card.$(SELECTORS.JOB_URL);
      if (!link) {
        this.logger.warn('Job card without a /jobs/view/ link.');
        continue;
      }

      const href = await link.evaluate((el) => el.getAttribute('href'));
      if (!href) {
        this.logger.warn('Job card link without href.');
        continue;
      }

      jobLinks.push(this.toAbsoluteUrl(href));
    }

    return jobLinks;
  }

  /**
   * Convierte una URL relativa de LinkedIn en absoluta.
   * Ejemplo: "/jobs/view/4462988555/?eBP=..." →
   *          "https://www.linkedin.com/jobs/view/4462988555/?eBP=..."
   */
  private toAbsoluteUrl(href: string): string {
    if (href.startsWith('http://') || href.startsWith('https://')) {
      return href;
    }
    return `${LINKEDIN_BASE_URL}${href.startsWith('/') ? '' : '/'}${href}`;
  }

  /**
   * Hace scroll automático dentro del contenedor de resultados para cargar
   * más tarjetas (el contenedor tiene su propio scroll container).
   */
  private async autoScroll(page: Page): Promise<void> {
    await page.evaluate(
      async (containerSelector: string) => {
        const container = document.querySelector(containerSelector);
        if (!container) return;

        await new Promise<void>((resolve) => {
          let totalHeight = 0;
          const distance = 500;
          const timer = setInterval(() => {
            const scrollHeight = container.scrollHeight;
            container.scrollBy(0, distance);
            totalHeight += distance;

            if (totalHeight >= scrollHeight) {
              clearInterval(timer);
              resolve();
            }
          }, 200);
        });
      },
      SELECTORS.RESULTS_CONTAINER,
    );
  }
}
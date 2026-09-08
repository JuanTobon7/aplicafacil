import { Injectable, Logger } from '@nestjs/common';
import { Page } from 'puppeteer';
import { LinkedInSearchParams } from '../../../dto/params.lindkln.search';
import { JobSearchComponent } from '../contract/job.search.component';

@Injectable()
export class JobSearchComponentImpl implements JobSearchComponent {
  private readonly logger = new Logger(JobSearchComponentImpl.name);

  private readonly JOBS_URL = 'https://www.linkedin.com/jobs/search/';

  /**
   * Contenedor de la lista de resultados (nuevo DOM LazyColumn de LinkedIn).
   * Actúa como la "ul" que agrupa todas las tarjetas de empleo.
   */
  private readonly RESULTS_CONTAINER_SELECTOR =
    '[componentkey="SearchResultsMainContent"]';

  /**
   * Selector de cada tarjeta de empleo dentro del contenedor de resultados.
   * El jobId se extrae del atributo componentkey: job-card-component-ref-<jobId>.
   */
  private readonly JOB_CARD_SELECTOR =
    'div[componentkey^="job-card-component-ref-"]';

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

    await page.goto(url, {
      waitUntil: 'networkidle2',
      timeout: 60_000,
    });

    // Esperar a que cargue la lista de resultados (nuevo DOM LazyColumn)
    await page.waitForSelector(this.RESULTS_CONTAINER_SELECTOR, {
      timeout: 30_000,
    });

    // Scroll para cargar más resultados
    await this.autoScroll(page);

    // Iterar sobre las tarjetas de empleo, hacer click en cada una
    // y extraer el jobId para construir la URL de la vacante
    const jobLinks = await this.collectJobLinks(page);

    this.logger.log(`Found ${jobLinks.length} job links.`);
    return jobLinks;
  }

  /**
   * Itera sobre las tarjetas de empleo dentro del contenedor de resultados.
   *
   * Por cada tarjeta:
   * 1. Extrae el jobId del atributo `componentkey="job-card-component-ref-<jobId>"`.
   * 2. Hace click en la tarjeta para que LinkedIn cargue el detalle en el
   *    panel derecho (necesario en el nuevo DOM LazyColumn).
   * 3. Construye la URL de la vacante: https://www.linkedin.com/jobs/view/<jobId>.
   */
  private async collectJobLinks(page: Page): Promise<string[]> {
    const jobLinks: string[] = [];

    const cards = await page.$$(
      `${this.RESULTS_CONTAINER_SELECTOR} ${this.JOB_CARD_SELECTOR}`,
    );

    this.logger.log(`Found ${cards.length} job cards in the results list.`);

    for (const card of cards) {
      const componentKey = await card.evaluate((el) =>
        el.getAttribute('componentkey'),
      );
      const match = /job-card-component-ref-(\d+)/.exec(componentKey ?? '');
      if (!match) {
        this.logger.warn(
          `Job card without valid componentkey: ${componentKey}`,
        );
        continue;
      }

      const jobId = match[1];
      jobLinks.push(`https://www.linkedin.com/jobs/view/${jobId}`);

      // Hacer click en la tarjeta para cargar el detalle en el panel derecho
      try {
        await card.click();
        // Pequeña espera para que el detalle se cargue
        await new Promise((resolve) => setTimeout(resolve, 500));
      } catch (error) {
        this.logger.warn(`Could not click job card ${jobId}: ${error}`);
      }
    }

    return jobLinks;
  }

  /**
   * Hace scroll automático dentro del contenedor de resultados para cargar
   * más tarjetas (el nuevo DOM LazyColumn tiene su propio scroll container).
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
      this.RESULTS_CONTAINER_SELECTOR,
    );
  }
}
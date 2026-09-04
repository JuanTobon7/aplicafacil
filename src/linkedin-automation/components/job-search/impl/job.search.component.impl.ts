import { Injectable, Logger } from '@nestjs/common';
import { Page } from 'puppeteer';
import { LinkedInSearchParams } from '../../../dto/params.lindkln.search';
import { JobSearchComponent } from '../contract/job.search.component';

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

    const url = `${this.JOBS_URL}?keywords=&location=${locationParam}&${remoteParam}&f_TPR=${timeFilter}&f_AL=${params.easyApply}`;

    await page.goto(url, {
      waitUntil: 'networkidle2',
      timeout: 60_000,
    });

    // Esperar a que cargue la lista de resultados
    await page.waitForSelector('.jobs-search-results-list', {
      timeout: 30_000,
    });

    // Scroll para cargar más resultados
    await this.autoScroll(page);

    // Extraer los enlaces de las vacantes
    const jobLinks = await page.evaluate(() => {
      const links = Array.from(
        document.querySelectorAll(
          '.jobs-search-results-list .job-card-container a.job-card-list__title',
        ),
      );
      return links
        .map((a) => (a as HTMLAnchorElement).href)
        .filter((href): href is string => !!href);
    });

    this.logger.log(`Found ${jobLinks.length} job links.`);
    return jobLinks;
  }

  /**
   * Hace scroll automático para cargar más resultados.
   */
  private async autoScroll(page: Page): Promise<void> {
    await page.evaluate(async () => {
      await new Promise<void>((resolve) => {
        let totalHeight = 0;
        const distance = 500;
        const timer = setInterval(() => {
          const scrollHeight = document.body.scrollHeight;
          window.scrollBy(0, distance);
          totalHeight += distance;

          if (totalHeight >= scrollHeight) {
            clearInterval(timer);
            resolve();
          }
        }, 200);
      });
    });
  }
}
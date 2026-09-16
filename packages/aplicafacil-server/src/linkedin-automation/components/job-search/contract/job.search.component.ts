import { Page } from 'puppeteer';
import { LinkedInSearchParams } from '../../../dto/params.lindkln.search';

export abstract class JobSearchComponent {
  /**
   * Navega a la página de búsqueda de empleos, aplica los filtros,
   * extrae las URLs de las tarjetas de la lista y las devuelve.
   */
  abstract searchJobs(
    page: Page,
    params: LinkedInSearchParams,
  ): Promise<string[]>;

  /**
   * Hace click en la tarjeta de empleo correspondiente al jobId para
   * cargar su detalle en el panel derecho (navegación entre tarjetas).
   */
  abstract clickJobCard(page: Page, jobId: string): Promise<void>;
}
import { Page } from 'puppeteer';
import { JobPostingDto } from 'src/jobs/dto/req/job..osting.dto';

export abstract class JobDetailExtractorComponent {
  /**
   * Navega a la URL de la vacante y extrae toda su información.
   */
  abstract extractJob(page: Page, url: string): Promise<JobPostingDto | null>;

  /**
   * Lee el detalle de la vacante ya cargada en el panel derecho
   * (two-pane) SIN navegar. Requiere que previamente se haya hecho
   * click en la tarjeta correspondiente.
   */
  abstract extractJobFromPanel(
    page: Page,
    url: string,
  ): Promise<JobPostingDto | null>;
}
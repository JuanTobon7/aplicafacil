import { Page } from 'puppeteer';
import { JobPostingDto } from 'src/jobs/dto/req/job..osting.dto';

export abstract class EasyApplyFormFiller {
  /**
   * Llena el formulario de Easy Apply de LinkedIn.
   * Retorna true si se envió la aplicación, false si el flujo terminó sin enviar.
   */
  abstract fill(page: Page, job: JobPostingDto): Promise<boolean>;
}
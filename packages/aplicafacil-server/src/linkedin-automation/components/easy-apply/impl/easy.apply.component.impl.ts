import { Injectable, Logger } from '@nestjs/common';
import { Page } from 'puppeteer';
import { JobPostingDto } from 'src/jobs/dto/req/job..osting.dto';
import { EasyApplyComponent } from '../contract/easy.apply.component';
import { EasyApplyButtonClicker } from '../contract/easy.apply.button.clicker';
import { EasyApplyFormFiller } from '../contract/easy.apply.form.filler';
import { HumanBehaviorService } from '../../../common/human-behavior.service';

@Injectable()
export class EasyApplyComponentImpl implements EasyApplyComponent {
  private readonly logger = new Logger(EasyApplyComponentImpl.name);

  constructor(
    private readonly buttonClicker: EasyApplyButtonClicker,
    private readonly formFiller: EasyApplyFormFiller,
    private readonly human: HumanBehaviorService,
  ) {}

  async apply(page: Page, job: JobPostingDto): Promise<void> {
    this.logger.log(`Applying to: ${job.job.title}`);

    try {
      // NOTA: 'domcontentloaded' en lugar de 'networkidle2' (LinkedIn mantiene
      // conexiones persistentes que impiden alcanzar 'idle' en la red).
      await page.goto(job.source.url, {
        waitUntil: 'domcontentloaded',
        timeout: 60_000,
      });

      // Pausa humana tras cargar la oferta antes de interactuar.
      await this.human.wait();

      this.logger.log('Going to click easy apply')

      const clicked = await this.buttonClicker.click(page);

      if (!clicked) {
        // No hay botón de Easy Apply: puede ser "Solicitud externa" o ya aplicado.
        // Lanzamos error para que el retry handler NO marque APPLIED.
        throw new Error(
          `No Easy Apply button found for ${job.job.title}. ` +
            'The job may require an external application or is already applied.',
        );
      }

      await page.waitForSelector('.jobs-easy-apply-modal', {
        timeout: 60_000,
      });

      // Pausa humana tras abrir el modal antes de rellenarlo.
      await this.human.wait();

      // Llenar los campos del formulario
      const submitted = await this.formFiller.fill(page, job);

      if (!submitted) {
        throw new Error(
          `Easy Apply form was not submitted for ${job.job.title}. ` +
            'The flow ended without reaching the submit button.',
        );
      }

      this.logger.log(`Successfully applied to: ${job.job.title}`);
    } catch (error) {
      this.logger.error(`Error applying to ${job.job.title}: ${error}`);
      throw error;
    }
  }
}
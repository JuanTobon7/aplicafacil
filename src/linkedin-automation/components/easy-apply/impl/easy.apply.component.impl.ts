import { Injectable, Logger } from '@nestjs/common';
import { Page } from 'puppeteer';
import { JobPostingDto } from 'src/jobs/dto/req/job..osting.dto';
import { EasyApplyComponent } from '../contract/easy.apply.component';

@Injectable()
export class EasyApplyComponentImpl implements EasyApplyComponent {
  private readonly logger = new Logger(EasyApplyComponentImpl.name);

  async apply(page: Page, job: JobPostingDto): Promise<void> {
    this.logger.log(`Applying to: ${job.job.title}`);

    try {
      // NOTA: 'domcontentloaded' en lugar de 'networkidle2' (LinkedIn mantiene
      // conexiones persistentes que impiden alcanzar 'idle' en la red).
      await page.goto(job.source.url, {
        waitUntil: 'domcontentloaded',
        timeout: 60_000,
      });

      // Buscar el botón de "Easy Apply" (Solicitud sencilla).
      // LinkedIn cambia las clases CSS dinámicamente, así que buscamos
      // por texto estable en lugar de depender de una clase concreta.
      const clicked = await page.evaluate(() => {
        const buttons = Array.from(document.querySelectorAll('button'));
        const btn = buttons.find((b) => {
          const text = (b.textContent ?? '').trim().toLowerCase();
          return (
            text.includes('solicitud sencilla') ||
            text.includes('easy apply')
          );
        });

        if (btn) {
          (btn as HTMLButtonElement).click();
          return true;
        }
        return false;
      });

      if (!clicked) {
        this.logger.warn(`No Easy Apply button found for ${job.job.title}`);
        return;
      }

      await page.waitForSelector('.jobs-easy-apply-modal', {
        timeout: 30_000,
      });

      // Llenar los campos del formulario
      await this.fillEasyApplyForm(page, job);

      this.logger.log(`Successfully applied to: ${job.job.title}`);
    } catch (error) {
      this.logger.error(`Error applying to ${job.job.title}: ${error}`);
    }
  }

  /**
   * Llena el formulario de Easy Apply de LinkedIn.
   */
  private async fillEasyApplyForm(
    page: Page,
    job: JobPostingDto,
  ): Promise<void> {
    while (true) {
      await this.fillTextInputs(page, job);
      await this.fillSelects(page, job);

      const shouldContinue = await this.clickNextButton(page);
      if (!shouldContinue) {
        break;
      }

      // Esperar a que cargue el siguiente paso
      await new Promise((resolve) => setTimeout(resolve, 2000));
    }
  }

  /**
   * Llena los inputs de texto del formulario Easy Apply.
   */
  private async fillTextInputs(page: Page, job: JobPostingDto): Promise<void> {
    const textInputs = await page.$$(
      '.jobs-easy-apply-modal input[type="text"], .jobs-easy-apply-modal input[type="tel"], .jobs-easy-apply-modal input[type="email"], .jobs-easy-apply-modal textarea',
    );

    for (const input of textInputs) {
      const name = await input.evaluate((el) => el.getAttribute('name'));
      const placeholder = await input.evaluate((el) =>
        el.getAttribute('placeholder'),
      );

      const value = this.resolveFieldValue(name, placeholder, job);
      if (value) {
        await input.click();
        await input.press('Control');
        await input.press('KeyA');
        await input.press('Control');
        await input.type(value, { delay: 30 });
      }
    }
  }

  /**
   * Llena los selects del formulario Easy Apply.
   */
  private async fillSelects(page: Page, job: JobPostingDto): Promise<void> {
    const selects = await page.$$('.jobs-easy-apply-modal select');

    for (const select of selects) {
      const name = await select.evaluate((el) => el.getAttribute('name'));
      const value = this.resolveSelectValue(name, job);
      if (value) {
        await select.select(value);
      }
    }
  }

  /**
   * Hace clic en el botón de continuar o enviar.
   * Retorna true si hay más pasos, false si se envió la aplicación.
   */
  private async clickNextButton(page: Page): Promise<boolean> {
    const nextButton = await page.$(
      '.jobs-easy-apply-modal button[aria-label="Continue to next step"], .jobs-easy-apply-modal button[aria-label="Review your application"], .jobs-easy-apply-modal button[aria-label="Submit application"]',
    );

    if (!nextButton) {
      return false;
    }

    const buttonText = await nextButton.evaluate((el) =>
      el.textContent?.trim(),
    );

    await nextButton.click();

    return !/submit|enviar/i.test(buttonText ?? '');
  }

  /**
   * Resuelve el valor de un campo de texto basado en su nombre o placeholder.
   */
  private resolveFieldValue(
    name: string | null,
    placeholder: string | null,
    job: JobPostingDto,
  ): string | null {
    const fieldName = (name ?? '').toLowerCase();
    const fieldPlaceholder = (placeholder ?? '').toLowerCase();

    if (
      fieldName.includes('phone') ||
      fieldPlaceholder.includes('phone') ||
      fieldPlaceholder.includes('tel')
    ) {
      return process.env.LINKEDIN_PHONE ?? '';
    }

    if (
      fieldName.includes('email') ||
      fieldPlaceholder.includes('email')
    ) {
      return process.env.LINKEDIN_EMAIL ?? '';
    }

    if (
      fieldName.includes('first') ||
      fieldPlaceholder.includes('first')
    ) {
      return process.env.LINKEDIN_FIRST_NAME ?? '';
    }

    if (
      fieldName.includes('last') ||
      fieldPlaceholder.includes('last')
    ) {
      return process.env.LINKEDIN_LAST_NAME ?? '';
    }

    if (
      fieldName.includes('city') ||
      fieldPlaceholder.includes('city')
    ) {
      return job.location.city ?? '';
    }

    if (
      fieldName.includes('country') ||
      fieldPlaceholder.includes('country')
    ) {
      return job.location.country ?? '';
    }

    return null;
  }

  /**
   * Resuelve el valor de un select basado en su nombre.
   */
  private resolveSelectValue(
    name: string | null,
    job: JobPostingDto,
  ): string | null {
    const fieldName = (name ?? '').toLowerCase();

    if (fieldName.includes('country')) {
      return job.location.country ?? null;
    }

    if (fieldName.includes('state') || fieldName.includes('province')) {
      return job.location.state ?? null;
    }

    if (fieldName.includes('city')) {
      return job.location.city ?? null;
    }

    return null;
  }
}
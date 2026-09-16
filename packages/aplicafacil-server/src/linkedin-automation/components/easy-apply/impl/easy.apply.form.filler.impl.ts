import { Injectable } from '@nestjs/common';
import { Page } from 'puppeteer';
import { JobPostingDto } from 'src/jobs/dto/req/job..osting.dto';
import { EasyApplyFormFiller } from '../contract/easy.apply.form.filler';
import { HumanBehaviorService } from '../../../common/human-behavior.service';

@Injectable()
export class EasyApplyFormFillerImpl implements EasyApplyFormFiller {
  constructor(private readonly human: HumanBehaviorService) {}

  async fill(page: Page, job: JobPostingDto): Promise<boolean> {
    let submitted = false;

    while (true) {
      await this.fillTextInputs(page, job);
      await this.fillSelects(page, job);

      const result = await this.clickNextButton(page);
      if (result.submitted) {
        submitted = true;
        break;
      }
      if (!result.continue) {
        break;
      }

      // Esperar a que cargue el siguiente paso (pausa humana variable)
      await this.human.wait(1500, 3000);
    }

    return submitted;
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
        // Pausa humana antes de interactuar con el campo.
        await this.human.wait();
        await input.click();
        await input.press('Control');
        await input.press('KeyA');
        await input.press('Control');
        // Escribir con delay variable por tecla (comportamiento humano).
        await input.type(value, { delay: await this.human.typeDelay() });
        // Pausa humana tras terminar de escribir.
        await this.human.wait();
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
        // Pausa humana antes de cambiar el select.
        await this.human.wait();
        await select.select(value);
        await this.human.wait();
      }
    }
  }

  /**
   * Hace clic en el botón de continuar o enviar.
   * Retorna { continue: true } si hay más pasos,
   * { submitted: true } si se envió la aplicación,
   * { continue: false, submitted: false } si no se encontró botón.
   */
  private async clickNextButton(
    page: Page,
  ): Promise<{ continue: boolean; submitted: boolean }> {
    // Buscar por aria-label (inglés) o por texto visible (español/inglés)
    const nextButton = await page.evaluateHandle(() => {
      const buttons = Array.from(
        document.querySelectorAll('.jobs-easy-apply-modal button'),
      );

      const ariaLabels = [
        'continue to next step',
        'review your application',
        'submit application',
      ];

      const textPatterns = [
        /continuar/i,
        /siguiente/i,
        /revisar/i,
        /enviar/i,
        /submit/i,
        /next/i,
        /review/i,
      ];

      // 1. Por aria-label
      const byAria = buttons.find((b) => {
        const label = (b.getAttribute('aria-label') ?? '').toLowerCase();
        return ariaLabels.some((a) => label.includes(a));
      });
      if (byAria) return byAria;

      // 2. Por texto visible
      return buttons.find((b) => {
        const text = (b.textContent ?? '').trim().toLowerCase();
        return textPatterns.some((p) => p.test(text));
      });
    });

    const handle = nextButton as unknown as {
      asElement: () => Element | null;
    };
    const element = handle.asElement();
    if (!element) {
      return { continue: false, submitted: false };
    }

    const buttonText = await page.evaluate(
      (el) => el.textContent?.trim() ?? '',
      element,
    );

    // Pausa humana antes de pulsar continuar/enviar.
    await this.human.wait();
    await (element as unknown as { click: () => Promise<void> }).click();
    await this.human.wait();

    const isSubmit = /submit|enviar/i.test(buttonText);
    return isSubmit
      ? { continue: false, submitted: true }
      : { continue: true, submitted: false };
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
import { Injectable, Logger } from '@nestjs/common';
import { Page } from 'puppeteer';
import {
  CaptchaDetection,
  CaptchaDetectedError,
  CaptchaDetector,
} from '../contract/captcha.detector';

/**
 * Selectores estables del challenge de seguridad de LinkedIn.
 *
 * La página de CAPTCHA vive en `/checkpoint/challenge/` y su DOM usa
 * clases semánticas estables (no hashes ofuscados):
 *
 * - `#captcha-internal`        → iframe del CAPTCHA (reCAPTCHA)
 * - `#captcha-challenge`       → formulario de verificación
 * - `.body__banner--error`     → banner de error del challenge
 * - `h1` con texto de comprobación de seguridad
 *
 * Se detecta por URL + selectores para cubrir variantes (el iframe
 * puede tardar en cargar, pero la URL ya es señal suficiente).
 */
const CAPTCHA_URL_PATTERN = /\/checkpoint\/challenge\//;

const CAPTCHA_SELECTORS = [
  '#captcha-internal',
  '#captcha-challenge',
  '.body__banner--error',
] as const;

/** Tiempo máximo (ms) esperando a que un humano resuelva el CAPTCHA. */
const DEFAULT_RESOLUTION_TIMEOUT_MS = 5 * 60_000;

@Injectable()
export class CaptchaDetectorImpl implements CaptchaDetector {
  private readonly logger = new Logger(CaptchaDetectorImpl.name);

  async detect(page: Page): Promise<CaptchaDetection> {
    const url = page.url();

    // 1) Señal más fuerte: la URL del challenge.
    if (CAPTCHA_URL_PATTERN.test(url)) {
      return {
        detected: true,
        url,
        matchedSelector: 'url:/checkpoint/challenge/',
      };
    }

    // 2) Señal DOM: algunos flujos redirigen a una URL distinta o el
    //    challenge se muestra embebido. La inspección es rápida y no
    //    lanza errores si la página está a medio cargar.
    try {
      const matchedSelector = await page.evaluate((selectors) => {
        const found = selectors.find((s) =>
          document.querySelector(s),
        );
        return found ?? null;
      }, CAPTCHA_SELECTORS);

      if (matchedSelector) {
        const title = await page.evaluate(
          () =>
            document.querySelector('h1')?.textContent?.trim() ?? undefined,
        );
        return { detected: true, url, title, matchedSelector };
      }
    } catch {
      // Página a medio cargar o contexto destruido: no es CAPTCHA.
    }

    return { detected: false, url };
  }

  async assertNoCaptcha(page: Page): Promise<void> {
    const detection = await this.detect(page);
    if (!detection.detected) {
      return;
    }

    this.logger.warn(
      `LinkedIn CAPTCHA detected at ${detection.url}` +
        (detection.title ? ` — "${detection.title}"` : ''),
    );

    throw new CaptchaDetectedError(
      'LinkedIn interpuso un challenge de seguridad (CAPTCHA). ' +
        'La automatización se detiene para no interactuar con la verificación. ' +
        'Resuelve el CAPTCHA en el navegador y vuelve a intentarlo.',
      detection.url,
      detection.title,
    );
  }

  async waitForCaptchaResolution(
    page: Page,
    timeoutMs: number = DEFAULT_RESOLUTION_TIMEOUT_MS,
  ): Promise<boolean> {
    this.logger.log(
      `Waiting up to ${Math.round(timeoutMs / 1000)}s for a human to solve the LinkedIn CAPTCHA...`,
    );

    try {
      await page.waitForFunction(
        () => !window.location.pathname.includes('/checkpoint/challenge/'),
        { timeout: timeoutMs, polling: 2_000 },
      );
      this.logger.log('LinkedIn CAPTCHA resolved. Resuming automation.');
      return true;
    } catch {
      this.logger.warn('Timed out waiting for LinkedIn CAPTCHA resolution.');
      return false;
    }
  }
}
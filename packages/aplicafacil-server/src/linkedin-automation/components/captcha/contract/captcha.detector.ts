import { Page } from 'puppeteer';

/**
 * Resultado de la detección de un challenge de seguridad de LinkedIn.
 */
export interface CaptchaDetection {
  /** true si la página actual es un challenge de seguridad (CAPTCHA). */
  detected: boolean;
  /** URL actual de la página (para diagnóstico). */
  url: string;
  /** Título del challenge si se pudo leer (p. ej. "Vamos a hacer una comprobación rápida de seguridad"). */
  title?: string;
  /** Selector con el que se detectó (para diagnóstico). */
  matchedSelector?: string;
}

/**
 * Guard de seguridad para páginas de LinkedIn.
 *
 * LinkedIn interpone un challenge de seguridad (CAPTCHA) en
 * `https://www.linkedin.com/checkpoint/challenge/` cuando detecta
 * comportamiento automatizado. Este componente:
 *
 * 1. Detecta el challenge (URL + selectores estables del DOM).
 * 2. Expone `assertNoCaptcha()` para lanzar un error tipado
 *    (`CaptchaDetectedError`) en cuanto aparece, abortando el flujo
 *    actual (login, búsqueda, Easy Apply) en lugar de seguir
 *    interactuando con una página de verificación.
 * 3. Expone `waitForCaptchaResolution()` para pausar el flujo hasta
 *    que un humano resuelva el CAPTCHA en el navegador visible.
 */
export abstract class CaptchaDetector {
  /**
   * Comprueba si la página actual es un challenge de seguridad.
   * No lanza errores: solo inspecciona y devuelve el resultado.
   */
  abstract detect(page: Page): Promise<CaptchaDetection>;

  /**
   * Lanza `CaptchaDetectedError` si la página actual es un CAPTCHA.
   * Se debe llamar DESPUÉS de cada navegación (page.goto, clicks que
   * cambian de página) y antes de interactuar con el DOM.
   */
  abstract assertNoCaptcha(page: Page): Promise<void>;

  /**
   * Espera (con timeout) a que el CAPTCHA sea resuelto por un humano.
   * Devuelve true si la página salió del challenge, false si se agotó
   * el tiempo. NO lanza errores.
   */
  abstract waitForCaptchaResolution(
    page: Page,
    timeoutMs?: number,
  ): Promise<boolean>;
}

/**
 * Error tipado que indica que LinkedIn interpuso un CAPTCHA.
 *
 * Se lanza desde `CaptchaDetector.assertNoCaptcha()` para que los
 * flujos (login, búsqueda, Easy Apply) lo capturen y aborten de forma
 * controlada en lugar de fallar con un error genérico de selector.
 */
export class CaptchaDetectedError extends Error {
  constructor(
    message: string,
    readonly url: string,
    readonly title?: string,
  ) {
    super(message);
    this.name = 'CaptchaDetectedError';
  }
}
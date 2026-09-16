import { Page } from 'puppeteer';

export abstract class EasyApplyButtonClicker {
  /**
   * Espera a que aparezca el botón "Easy Apply" (Solicitud sencilla)
   * y hace clic en él.
   * Retorna true si se hizo clic, false si no se encontró el botón.
   */
  abstract click(page: Page): Promise<boolean>;
}
import { Injectable, Logger } from '@nestjs/common';
import { Page } from 'puppeteer';
import {
  LinkedInCredentials,
  LinkedInLoginComponent,
} from '../contract/linkedin.login.component';

@Injectable()
export class LinkedInLoginComponentImpl
  implements LinkedInLoginComponent
{
  private readonly logger = new Logger(
    LinkedInLoginComponentImpl.name,
  );

  private readonly LOGIN_URL = 'https://www.linkedin.com/login';

  async login(
    page: Page,
    credentials: LinkedInCredentials,
  ): Promise<void> {
    this.logger.log('Opening LinkedIn login...');

    await page.goto(this.LOGIN_URL, {
      waitUntil: 'domcontentloaded',
      timeout: 60_000,
    });

    /*
     * If LinkedIn redirects us away from /login,
     * there is already an authenticated session.
     */
    if (!page.url().includes('/login')) {
      this.logger.log('LinkedIn session already active.');
      return;
    }

    /*
     * Find the inputs by type.
     *
     * This doesn't depend on:
     * - IDs
     * - CSS classes
     * - Language
     */
    const emailSelector = 'input[type="email"]';
    const passwordSelector = 'input[type="password"]';

    await page.waitForSelector(emailSelector, {
      timeout: 30_000,
    });
    this.logger.log('Email input encontrado.');

    await page.waitForSelector(passwordSelector, {
      timeout: 30_000,
    });
    this.logger.log('Password input encontrado.');

    /*
     * Fill the inputs using the native value setter.
     *
     * LinkedIn inputs are React-controlled, so page.type()
     * writes to the DOM but React never registers the change
     * (it uses its own value tracker). Using the native setter
     * + dispatching an "input" event makes React see the value.
     */
    await this.fillInput(page, emailSelector, credentials.email);
    await this.fillInput(page, passwordSelector, credentials.password);

    this.logger.log('Credentials filled.');

    /*
     * Find and click the login button directly in the browser.
     *
     * We don't use:
     *
     * button[type="submit"]
     *
     * because LinkedIn currently uses type="button".
     *
     * We also don't depend on:
     * - generated CSS classes
     * - <form> wrappers
     */
    const clicked = await page.evaluate(() => {
      const buttons = Array.from(
        document.querySelectorAll('button'),
      );

      /*
       * Match EXACT text, not partial.
       *
       * LinkedIn also has "Iniciar sesión con Microsoft" /
       * "Sign in with Microsoft" buttons, and a partial match
       * would click the Microsoft OAuth button instead.
       */
      const btn = buttons.find((b) => {
        const text = (b.textContent ?? '').trim();
        return /^(iniciar sesión|sign in)$/i.test(text);
      });

      if (btn) {
        (btn as HTMLButtonElement).click();
        return true;
      }

      return false;
    });

    if (!clicked) {
      throw new Error('LinkedIn login button not found.');
    }

    this.logger.log('Botón "Iniciar sesión" encontrado y clickeado.');

    /*
     * Don't use waitForNavigation().
     *
     * LinkedIn authentication doesn't necessarily
     * trigger a traditional browser navigation.
     */
    await this.waitForLoginResult(page);

    this.logger.log(
      `LinkedIn login finished. Current URL: ${page.url()}`,
    );
  }

  /**
   * Llena un input controlado por React usando el native setter.
   *
   * page.type() no funciona con inputs de React porque React mantiene
   * su propio "value tracker" y no detecta cambios hechos directamente
   * en el DOM. Con el native setter + evento "input", React sí registra
   * el valor.
   */
  private async fillInput(
    page: Page,
    selector: string,
    value: string,
  ): Promise<void> {
    await page.evaluate(
      ({ selector, value }) => {
        const input = document.querySelector<HTMLInputElement>(
          selector,
        );

        if (!input) {
          return;
        }

        const nativeSetter = Object.getOwnPropertyDescriptor(
          HTMLInputElement.prototype,
          'value',
        )?.set;

        nativeSetter?.call(input, value);

        input.dispatchEvent(
          new Event('input', { bubbles: true }),
        );
        input.dispatchEvent(
          new Event('change', { bubbles: true }),
        );
      },
      { selector, value },
    );
  }

  private async waitForLoginResult(
    page: Page,
  ): Promise<void> {
    try {
      await page.waitForFunction(
        () => !window.location.pathname.includes('/login'),
        {
          timeout: 60_000,
        },
      );
    } catch {
      throw new Error(
        `LinkedIn login did not finish. Current URL: ${page.url()}`,
      );
    }
  }
}

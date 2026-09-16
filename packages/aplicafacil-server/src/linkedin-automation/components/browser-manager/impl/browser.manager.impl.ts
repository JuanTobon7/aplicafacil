import { Inject, Injectable, Logger } from '@nestjs/common';
import puppeteer, { Browser, Page } from 'puppeteer';
import { addExtra } from 'puppeteer-extra';
import StealthPlugin from 'puppeteer-extra-plugin-stealth';
import { BrowserManager } from '../contract/browser.manager';
import { CaptchaDetector } from '../../captcha/contract/captcha.detector';

/**
 * Tipos de recursos pesados que bloqueamos para reducir el consumo de red
 * y acelerar la carga de las páginas de LinkedIn.
 *
 * LinkedIn carga muchas imágenes (avatares, logos, banners), fuentes y media
 * que no necesitamos para scraping. Bloquearlos ahorra ancho de banda y CPU.
 */
const BLOCKED_RESOURCE_TYPES = new Set([
  'image',
  'media',
  'font',
  'texttrack',
  'eventsource',
  'manifest',
]);

/**
 * Instancia de Puppeteer "extra" con el plugin stealth.
 *
 * `puppeteer-extra` envuelve la instancia local de Puppeteer (v25) y le
 * añade el plugin stealth, que parchea las huellas de automatización que
 * los anti-bots (incluido el challenge de LinkedIn) detectan:
 *
 * - `navigator.webdriver` → false
 * - plugins y lenguajes del navegador
 * - WebGL, Chrome runtime, permisos, etc.
 *
 * NOTA: con Puppeteer v25 NO se usa `require('puppeteer-extra').default`
 * con el puppeteer global; se pasa la instancia local con `addExtra()`.
 */
const puppeteerExtra = addExtra(puppeteer);
puppeteerExtra.use(StealthPlugin());

@Injectable()
export class BrowserManagerImpl implements BrowserManager {
  private readonly logger = new Logger(BrowserManagerImpl.name);

  /**
   * Páginas en las que el bloqueo de recursos está desactivado.
   *
   * Cuando LinkedIn interpone un CAPTCHA necesitamos que carguen las
   * imágenes y demás recursos para que el humano pueda ver y resolver
   * la verificación. Mientras la página esté en este conjunto, no se
   * aborta ningún recurso.
   */
  private readonly resourceBlockingDisabled = new WeakSet<Page>();

  constructor(
    @Inject(CaptchaDetector)
    private readonly captchaDetector: CaptchaDetector,
  ) {}

  /**
   * Indica si una URL pertenece al challenge de seguridad de LinkedIn.
   */
  private isCaptchaUrl(url: string): boolean {
    return url.includes('/checkpoint/challenge/');
  }

  async launch(): Promise<Browser> {
    this.logger.log('Launching browser...');
    return puppeteerExtra.launch({
      headless: false,
      defaultViewport: { width: 1280, height: 800 },
      args: ['--no-sandbox', '--disable-setuid-sandbox'],
    });
  }

  async newPage(browser: Browser): Promise<Page> {
    const page = await browser.newPage();

    // NOTA: NO reenviamos los console.* de la página del navegador a Node.
    // LinkedIn loguea muchísimo ruido interno (tracking, telemetría, warnings
    // de proto attributes...) que inundaría la terminal. Los logs que nos
    // interesan (clicker, form filler, etc.) se emiten desde Node con el
    // Logger de NestJS, no desde dentro del navegador.

    // Set a realistic user agent to avoid bot detection
    await page
      .setUserAgent({
        userAgent:
          'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
        platform: 'Windows',
      })
      .catch(() => undefined);

    // Bloquear recursos pesados (imágenes, fuentes, media, trackers) para
    // reducir el consumo de red. Se hace ANTES de cualquier navegación.
    //
    // EXCEPCIÓN: si la página está en un CAPTCHA (o navegando hacia uno),
    // dejamos pasar TODOS los recursos para que el humano pueda ver y
    // resolver la verificación (imágenes del reCAPTCHA, fuentes, etc.).
    await page.setRequestInterception(true);
    page.on('request', (request) => {
      const captchaActive =
        this.resourceBlockingDisabled.has(page) ||
        this.isCaptchaUrl(page.url()) ||
        this.isCaptchaUrl(request.url());

      if (
        !captchaActive &&
        BLOCKED_RESOURCE_TYPES.has(request.resourceType())
      ) {
        request.abort();
      } else {
        request.continue();
      }
    });

    // Guard de seguridad: cada página nueva queda protegida contra el
    // challenge de seguridad de LinkedIn (CAPTCHA). Si LinkedIn redirige
    // a /checkpoint/challenge/, se loguea y se pausa el flujo esperando
    // a que un humano lo resuelva en el navegador visible.
    page.on('framenavigated', async (frame) => {
      if (frame !== page.mainFrame()) {
        return;
      }
      try {
        const detection = await this.captchaDetector.detect(page);
        if (detection.detected) {
          this.logger.warn(
            `[CaptchaGuard] LinkedIn CAPTCHA detected at ${detection.url}` +
              (detection.title ? ` — "${detection.title}"` : ''),
          );
          // Permitir que carguen imágenes y demás recursos para que el
          // humano pueda ver y resolver el CAPTCHA en el navegador visible.
          this.resourceBlockingDisabled.add(page);
          // Pausa el flujo hasta que el usuario resuelva el CAPTCHA
          // (el navegador es visible: headless: false).
          await this.captchaDetector.waitForCaptchaResolution(page);
          // Restaurar el bloqueo de recursos tras resolver el CAPTCHA.
          this.resourceBlockingDisabled.delete(page);
        }
      } catch (error) {
        this.logger.warn(
          `[CaptchaGuard] Error checking CAPTCHA: ${
            error instanceof Error ? error.message : error
          }`,
        );
      }
    });

    return page;
  }

  async close(browser: Browser): Promise<void> {
    this.logger.log('Closing browser...');
    await browser.close();
  }
}
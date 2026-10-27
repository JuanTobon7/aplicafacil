import type { Browser, BrowserContext, BrowserContextOptions } from "playwright";
import type { AppLogger } from "../../application/port/out/app-logger";
import type { BrowserInstaller } from "../../application/port/out/browser-installer";
import type { BrowserManager } from "../../application/port/out/browser-manager";
import type { BrowserSession } from "../../application/port/out/browser-session";
import type { OpenSessionOptions } from "../../application/port/out/browser-session-factory";
import type { BrowserState, BrowserStatus } from "../../domain/browser-status";
import { ScraperError } from "../../application/exception/scraper-error";
import { desktopUserAgent, type BrowserLauncher } from "./chromium-launcher";
import { PlaywrightBrowserSession } from "./playwright-browser-session";

export type BrowserManagerDeps = {
  installer: BrowserInstaller;
  launcher: BrowserLauncher;
  logger: AppLogger;
};

/**
 * Gestor ÚNICO (Singleton) del navegador en el proceso.
 *
 * - Instala Chromium al arrancar y expone su estado.
 * - Headless: comparte un solo proceso de Chromium y entrega un contexto aislado por sesión (no se relanza el
 *   navegador en cada validación). Si el proceso muere, se relanza en la siguiente petición.
 * - Visible: un proceso propio por sesión, que se cierra con ella (la persona cierra la ventana = fin).
 * - Nunca usa launchPersistentContext: todo contexto nace vacío o desde un storageState en memoria.
 * - Al apagar la app cierra todo lo que siga abierto.
 *
 * Las dependencias se inyectan en la primera llamada a {@link getInstance} desde la composición (DIP); el resto
 * de la app no conoce esta clase, solo los puertos BrowserManager / BrowserSessionFactory / BrowserStatusProvider.
 */
export class PlaywrightBrowserManager implements BrowserManager {
  private static instance?: PlaywrightBrowserManager;

  private state: BrowserState = "INSTALLING";
  private failure?: string;
  private sharedHeadless?: Promise<Browser>;
  private readonly openSessions = new Set<BrowserSession>();

  private constructor(private readonly deps: BrowserManagerDeps) {}

  static getInstance(deps?: BrowserManagerDeps): PlaywrightBrowserManager {
    if (!PlaywrightBrowserManager.instance) {
      if (!deps) throw new Error("PlaywrightBrowserManager no está inicializado: pásale sus dependencias");
      PlaywrightBrowserManager.instance = new PlaywrightBrowserManager(deps);
    }
    return PlaywrightBrowserManager.instance;
  }

  /** Solo para pruebas: olvida la instancia actual. */
  static resetInstance() {
    PlaywrightBrowserManager.instance = undefined;
  }

  async initialize(): Promise<void> {
    this.state = "INSTALLING";
    this.failure = undefined;
    this.deps.logger.log("Verificando Chromium (la primera vez se descarga)…");
    try {
      await this.deps.installer.install();
      this.state = "READY";
      this.deps.logger.log("Chromium listo");
    } catch (error) {
      this.state = "FAILED";
      this.failure = (error as Error).message;
      this.deps.logger.error(
        `No se pudo instalar Chromium: ${this.failure}. Instálalo con: npx playwright install chromium`,
      );
    }
  }

  status(): BrowserStatus {
    return { browser: this.state, ...(this.failure && { detail: this.failure }) };
  }

  assertReady() {
    if (this.state !== "READY") {
      throw new ScraperError(
        "BROWSER_NOT_READY",
        this.state === "INSTALLING" ? "Se está instalando Chromium." : "Falló la instalación de Chromium.",
      );
    }
  }

  async openSession({ visible, storageState }: OpenSessionOptions): Promise<BrowserSession> {
    this.assertReady();
    const options: BrowserContextOptions = {
      ...(visible && { viewport: null }),
      // El storageState llega opaco por HTTP; Playwright valida su forma
      ...(storageState && { storageState: storageState as BrowserContextOptions["storageState"] }),
    };
    return visible ? this.openVisible(options) : this.openHeadless(options);
  }

  async shutdown(): Promise<void> {
    await Promise.all([...this.openSessions].map((session) => session.close().catch(() => undefined)));
    const browser = await this.sharedHeadless?.catch(() => undefined);
    this.sharedHeadless = undefined;
    await browser?.close().catch(() => undefined);
  }

  private async openVisible(options: BrowserContextOptions): Promise<BrowserSession> {
    const browser = await this.deps.launcher.launch({ headless: false });
    try {
      const context = await browser.newContext(options);
      return this.track(context, browser, () => browser.close());
    } catch (error) {
      await browser.close().catch(() => undefined);
      throw error;
    }
  }

  private async openHeadless(options: BrowserContextOptions): Promise<BrowserSession> {
    const browser = await this.headlessBrowser();
    // Con "HeadlessChrome" en el user-agent algunos portales responden 403 (Computrabajo lo hace)
    const context = await browser.newContext({ ...options, userAgent: desktopUserAgent(browser.version()) });
    return this.track(context, browser, () => context.close());
  }

  private headlessBrowser(): Promise<Browser> {
    this.sharedHeadless ??= this.deps.launcher.launch({ headless: true }).then(
      (browser) => {
        browser.on("disconnected", () => (this.sharedHeadless = undefined));
        return browser;
      },
      (error) => {
        this.sharedHeadless = undefined;
        throw error;
      },
    );
    return this.sharedHeadless;
  }

  private track(context: BrowserContext, browser: Browser, close: () => Promise<void>): BrowserSession {
    const session: BrowserSession = new PlaywrightBrowserSession(
      context,
      () => browser.isConnected(),
      async () => {
        this.openSessions.delete(session);
        await close();
      },
    );
    this.openSessions.add(session);
    return session;
  }
}

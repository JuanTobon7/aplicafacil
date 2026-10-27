import type {
  BrowserPage,
  ExtractedRecord,
  FieldSpec,
  NavigationOptions,
  SpotlightOptions,
} from "../../src/application/port/out/browser-page";
import type {
  BrowserSession,
  SessionCookie,
  SessionHttpResponse,
} from "../../src/application/port/out/browser-session";
import type {
  BrowserSessionFactory,
  OpenSessionOptions,
} from "../../src/application/port/out/browser-session-factory";
import type { Clock } from "../../src/application/port/out/clock";
import { ScraperError } from "../../src/application/exception/scraper-error";
import type { StorageState } from "../../src/domain/storage-state";

/** Sitio simulado: qué devuelve cada URL y qué cookies hay. */
export type FakeSite = {
  cookies?: SessionCookie[];
  /** url → JSON de la respuesta; las que no estén responden 404. */
  api?: Record<string, unknown>;
  /** url → html de la página. */
  pages?: Record<string, string>;
  /** url → url final tras redirección. */
  redirects?: Record<string, string>;
  /** Texto por selector, en cualquier página. */
  texts?: Record<string, string>;
  /** url → selector → registros que devuelve extractAll (ya con los nombres de campo). */
  lists?: Record<string, Record<string, ExtractedRecord[]>>;
};

export class FakeBrowserPage implements BrowserPage {
  private current = "about:blank";
  closed = false;

  constructor(private readonly site: FakeSite) {}

  async goto(url: string, _options?: NavigationOptions) {
    this.current = this.site.redirects?.[url] ?? url;
  }

  url() {
    return this.current;
  }

  async html() {
    return this.site.pages?.[this.current] ?? "";
  }

  async textOf(selector: string) {
    return this.site.texts?.[selector] ?? null;
  }

  readonly visited: string[] = [];
  scrolls = 0;

  async waitFor(selector: string, _timeoutMs: number) {
    return (this.site.lists?.[this.current]?.[selector]?.length ?? 0) > 0 || this.site.texts?.[selector] !== undefined;
  }

  async scrollToEnd() {
    this.scrolls++;
  }

  async extractAll(selector: string, _fields: Record<string, FieldSpec>) {
    this.visited.push(this.current);
    return this.site.lists?.[this.current]?.[selector] ?? [];
  }

  readonly spotlighted: { selector: string; index: number; options: SpotlightOptions }[] = [];

  async spotlight(selector: string, index: number, options: SpotlightOptions) {
    this.spotlighted.push({ selector, index, options });
  }

  async close() {
    this.closed = true;
  }
}

export class FakeBrowserSession implements BrowserSession {
  readonly pages: FakeBrowserPage[] = [];
  open = true;
  closeCalls = 0;
  state: StorageState = { cookies: [], origins: [] };

  constructor(public site: FakeSite = {}) {}

  async newPage() {
    const page = new FakeBrowserPage(this.site);
    this.pages.push(page);
    return page;
  }

  currentUrl() {
    return this.pages.at(-1)?.url() ?? null;
  }

  async cookies(_origin: string) {
    return this.site.cookies ?? [];
  }

  async get(url: string): Promise<SessionHttpResponse> {
    const body = this.site.api?.[url];
    return { ok: body !== undefined, status: body !== undefined ? 200 : 404, json: async () => body };
  }

  isOpen() {
    return this.open;
  }

  async exportState() {
    return this.state;
  }

  async close() {
    this.closeCalls++;
    this.open = false;
  }
}

/** Sesión cuyo motor falla en todo: para verificar que los portales no rompen su contrato. */
export class BrokenBrowserSession extends FakeBrowserSession {
  override async newPage(): Promise<FakeBrowserPage> {
    throw new Error("Target page, context or browser has been closed");
  }
  override async cookies(): Promise<SessionCookie[]> {
    throw new Error("boom");
  }
  override async get(): Promise<SessionHttpResponse> {
    throw new Error("boom");
  }
}

export class FakeSessionFactory implements BrowserSessionFactory {
  ready = true;
  readonly opened: { options: OpenSessionOptions; session: FakeBrowserSession }[] = [];

  constructor(private readonly next: () => FakeBrowserSession = () => new FakeBrowserSession()) {}

  assertReady() {
    if (!this.ready) throw new ScraperError("BROWSER_NOT_READY", "Se está instalando Chromium.");
  }

  async openSession(options: OpenSessionOptions) {
    this.assertReady();
    const session = this.next();
    this.opened.push({ options, session });
    return session;
  }
}

/** Reloj que avanza solo cuando se duerme: los timeouts se prueban sin esperar. */
export class FakeClock implements Clock {
  current = 0;
  onSleep?: () => void;

  now() {
    return this.current;
  }

  async sleep(ms: number) {
    this.current += ms;
    this.onSleep?.();
  }
}

export const silentLogger = { log: () => undefined, warn: () => undefined, error: () => undefined };

import type { BrowserContext } from "playwright";
import type { BrowserPage } from "../../application/port/out/browser-page";
import type {
  BrowserSession,
  SessionCookie,
  SessionHttpResponse,
} from "../../application/port/out/browser-session";
import type { StorageState } from "../../domain/storage-state";
import { PlaywrightBrowserPage } from "./playwright-browser-page";

/** BrowserSession sobre un BrowserContext de Playwright. Cómo se libera lo decide quien la crea. */
export class PlaywrightBrowserSession implements BrowserSession {
  private closing?: Promise<void>;

  constructor(
    private readonly context: BrowserContext,
    private readonly isConnected: () => boolean,
    private readonly release: () => Promise<void>,
  ) {}

  async newPage(): Promise<BrowserPage> {
    return new PlaywrightBrowserPage(await this.context.newPage());
  }

  currentUrl(): string | null {
    return this.context.pages().at(-1)?.url() ?? null;
  }

  async cookies(origin: string): Promise<SessionCookie[]> {
    return (await this.context.cookies(origin)).map(({ name, value }) => ({ name, value }));
  }

  async get(url: string, headers?: Record<string, string>): Promise<SessionHttpResponse> {
    const response = await this.context.request.get(url, { headers });
    return { ok: response.ok(), status: response.status(), json: () => response.json() };
  }

  isOpen(): boolean {
    return !this.closing && this.isConnected() && this.context.pages().length > 0;
  }

  async exportState(): Promise<StorageState> {
    return this.context.storageState();
  }

  close(): Promise<void> {
    this.closing ??= this.release();
    return this.closing;
  }
}

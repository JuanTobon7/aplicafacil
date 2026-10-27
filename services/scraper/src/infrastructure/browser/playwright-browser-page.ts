import type { Page } from "playwright";
import type {
  BrowserPage,
  ExtractedRecord,
  FieldSpec,
  NavigationOptions,
  SpotlightOptions,
} from "../../application/port/out/browser-page";

/** Lo poco del DOM que usan las funciones que se ejecutan dentro de la página. */
type DomElement = {
  querySelector(selector: string): DomElement | null;
  getAttribute(name: string): string | null;
  textContent: string | null;
  scrollTop: number;
  clientHeight: number;
  scrollBy(x: number, y: number): void;
  style: { outline: string; outlineOffset: string };
};
type DomDocument = { querySelector(selector: string): DomElement | null; scrollingElement: DomElement | null };

export class PlaywrightBrowserPage implements BrowserPage {
  constructor(private readonly page: Page) {}

  async goto(url: string, options: NavigationOptions = {}) {
    await this.page.goto(url, { waitUntil: options.waitUntil, timeout: options.timeoutMs });
  }

  url() {
    return this.page.url();
  }

  html() {
    return this.page.content();
  }

  /** Sin esperar: textContent() de Playwright espera (30 s) a que aparezca y lanza; el contrato pide null. */
  async textOf(selector: string) {
    const element = this.page.locator(selector).first();
    if ((await element.count()) === 0) return null;
    return element.textContent({ timeout: 2_000 }).catch(() => null);
  }

  async waitFor(selector: string, timeoutMs: number) {
    try {
      await this.page.locator(selector).first().waitFor({ state: "attached", timeout: timeoutMs });
      return true;
    } catch {
      return false;
    }
  }

  async scrollToEnd(container?: string, maxSteps = 20) {
    for (let step = 0; step < maxSteps; step++) {
      // Corre dentro del navegador: el proyecto no compila con los tipos del DOM, de ahí los tipos mínimos
      const atEnd = await this.page.evaluate((selector) => {
        const doc = (globalThis as unknown as { document: DomDocument }).document;
        const el = selector ? doc.querySelector(selector) : doc.scrollingElement;
        if (!el) return true;
        const before = el.scrollTop;
        el.scrollBy(0, Math.max(400, el.clientHeight * 0.8));
        return el.scrollTop === before;
      }, container ?? null);
      if (atEnd) return;
      // Deja que el portal cargue el siguiente bloque perezoso
      await this.page.waitForTimeout(350);
    }
  }

  extractAll(selector: string, fields: Record<string, FieldSpec>): Promise<ExtractedRecord[]> {
    return this.page.locator(selector).evaluateAll((elements: unknown[], spec: Record<string, FieldSpec>) => {
      const read = (root: DomElement, field: FieldSpec): string | null => {
        const target = field.selector ? root.querySelector(field.selector) : root;
        if (!target) return null;
        const value = field.attribute ? target.getAttribute(field.attribute) : target.textContent;
        const clean = value?.replace(/\s+/g, " ").trim();
        return clean ? clean : null;
      };
      return (elements as DomElement[]).map((element) =>
        Object.fromEntries(Object.entries(spec).map(([name, field]) => [name, read(element, field)])),
      );
    }, fields);
  }

  async spotlight(selector: string, index: number, { open, close, holdMs }: SpotlightOptions) {
    const listUrl = this.page.url();
    const card = this.page.locator(selector).nth(index);
    try {
      await card.scrollIntoViewIfNeeded({ timeout: 3_000 });
      await card.evaluate((el: unknown) => {
        const style = (el as DomElement).style;
        style.outline = "3px solid #0a66c2";
        style.outlineOffset = "-3px";
      });
      if (open) await card.locator(open).first().click({ timeout: 3_000 });
      await this.page.waitForTimeout(holdMs);
      await card.evaluate((el: unknown) => ((el as DomElement).style.outline = "")).catch(() => undefined);
    } catch {
      // La card ya no está o no se pudo abrir: la grabación sigue con la siguiente
    }
    // Ventana angosta: el detalle tapó la lista y su botón vuelve a ella (el historial puede no tenerla)
    if (close && !(await card.isVisible().catch(() => false))) {
      await this.page.locator(close).first().click({ timeout: 3_000 }).catch(() => undefined);
    }
    if (new URL(this.page.url()).pathname !== new URL(listUrl).pathname) {
      await this.page.goBack({ waitUntil: "domcontentloaded" }).catch(() => undefined);
    }
  }

  close() {
    return this.page.close();
  }
}

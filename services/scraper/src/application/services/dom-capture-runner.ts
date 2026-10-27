import { sanitizeHtml } from "../../domain/dom-snapshot";
import type { BrowserPage } from "../port/out/browser-page";
import type { BrowserSession } from "../port/out/browser-session";
import type { BrowserSessionFactory } from "../port/out/browser-session-factory";
import type { Clock } from "../port/out/clock";
import type { CaptureOperator, CaptureScript, CaptureStep, DomSnapshotStore } from "../port/out/dom-capture";

const STEP_TIMEOUT_MS = 20_000;
const LOGIN_TIMEOUT_MS = 10 * 60_000;
const POLL_MS = 1_000;

/** Lo que el recorrido necesita, con nombre. */
export type DomCaptureDeps = {
  sessions: BrowserSessionFactory;
  store: DomSnapshotStore;
  operator: CaptureOperator;
  clock: Clock;
};

/** Lo que viaja junto durante un recorrido. */
type Capture = { script: CaptureScript; session: BrowserSession; page: BrowserPage; redact: readonly string[] };

/**
 * Recorrido automático para guardar el DOM de un portal: abre su página de inicio de sesión en una ventana visible,
 * espera a que la persona inicie sesión (la app nunca ve la contraseña) y, en cuanto lo detecta, visita cada paso del
 * guion y guarda cada página saneada. La persona solo inicia sesión; la ventana se cierra sola al terminar.
 */
export class DomCaptureRunner {
  private saved = 0;

  constructor(private readonly deps: DomCaptureDeps) {}

  /** @return cuántas páginas se guardaron */
  async run(script: CaptureScript, redact: readonly string[]): Promise<number> {
    this.saved = 0;
    const session = await this.deps.sessions.openSession({ visible: true });
    try {
      const capture: Capture = { script, session, page: await session.newPage(), redact };
      await capture.page.goto(script.loginUrl, { waitUntil: "domcontentloaded" });
      const initialCookieNames = await this.cookieNames(capture);
      await this.save(capture, "login");
      this.deps.operator.say(`Inicia sesión en ${script.displayName} en la ventana (2FA incluido) y no la cierres: `
        + "la captura sigue sola y la cierra al terminar.");
      await this.waitForLogin(capture, initialCookieNames);
      this.deps.operator.say("Sesión iniciada: recorriendo las páginas…");
      await this.save(capture, "sesion-iniciada");
      let previous = await capture.page.html();
      for (const step of script.steps) {
        previous = await this.runStep(capture, step, previous);
      }
      return this.saved;
    } finally {
      await session.close();
    }
  }

  /** Hasta que el guion detecte la sesión o llegue la señal manual. Cada cambio de URL o de cookies se muestra. */
  private async waitForLogin(capture: Capture, initialCookieNames: string[]) {
    const deadline = this.deps.clock.now() + LOGIN_TIMEOUT_MS;
    let last = "";
    for (;;) {
      if (!capture.session.isOpen()) throw new Error("Cerraste la ventana antes de terminar: vuelve a correr la captura");
      const probe = { url: capture.session.currentUrl() ?? "", cookieNames: await this.cookieNames(capture), initialCookieNames };
      last = this.report(probe.url, probe.cookieNames, last);
      if (capture.script.loggedIn(probe) || (await this.deps.operator.continueRequested())) return;
      if (this.deps.clock.now() > deadline) throw new Error("Pasaron 10 minutos sin detectar el inicio de sesión");
      await this.deps.clock.sleep(POLL_MS);
    }
  }

  /** Muestra la URL y los nombres de cookies cuando cambian: así se aprende qué indica la sesión en un portal nuevo. */
  private report(url: string, cookieNames: string[], last: string): string {
    const now = `${url} | cookies: ${cookieNames.join(", ")}`;
    if (now !== last) this.deps.operator.say(`  · ${now}`);
    return now;
  }

  private async runStep(capture: Capture, step: CaptureStep, previous: string): Promise<string> {
    const url = typeof step.url === "function" ? step.url(previous) : step.url;
    if (step.url && !url) {
      this.deps.operator.say(`Se omite "${step.name}": no se encontró a dónde ir en la página anterior.`);
      return previous;
    }
    this.deps.operator.say(`→ ${step.description}`);
    if (url) await capture.page.goto(url, { waitUntil: "domcontentloaded", timeoutMs: 45_000 });
    if (step.waitFor && !(await capture.page.waitFor(step.waitFor, STEP_TIMEOUT_MS))) {
      this.deps.operator.say(`  (no apareció "${step.waitFor}"; se guarda igual para revisar qué cambió)`);
    }
    await this.save(capture, step.name);
    return capture.page.html();
  }

  private async cookieNames(capture: Capture): Promise<string[]> {
    const origins = [capture.script.origin, ...(capture.script.extraCookieOrigins ?? [])];
    const lists = await Promise.all(origins.map((origin) => capture.session.cookies(origin)));
    return [...new Set(lists.flat().map((c) => c.name))].sort();
  }

  private async save(capture: Capture, name: string) {
    const html = await capture.page.html();
    const path = await this.deps.store.save({
      portal: capture.script.portal,
      step: `${String(this.saved).padStart(2, "0")}-${name}`,
      url: capture.page.url(),
      title: titleOf(html),
      cookieNames: await this.cookieNames(capture),
      html: sanitizeHtml(html, capture.redact),
    });
    this.saved++;
    this.deps.operator.say(`  guardado ${path}`);
  }
}

function titleOf(html: string): string {
  return html.match(/<title[^>]*>([^<]*)<\/title>/i)?.[1]?.trim() ?? "";
}

import { afterEach, describe, expect, it, vi } from "vitest";
import type { Browser } from "playwright";
import { desktopUserAgent, type BrowserLauncher } from "../../../src/infrastructure/browser/chromium-launcher";
import {
  PlaywrightBrowserManager,
  type BrowserManagerDeps,
} from "../../../src/infrastructure/browser/playwright-browser-manager";
import { silentLogger } from "../../support/fake-browser";

/** Navegador de Playwright simulado: solo lo que usa el gestor. */
function fakeBrowser() {
  const listeners: Record<string, () => void> = {};
  const contexts: { options: unknown; close: ReturnType<typeof vi.fn> }[] = [];
  let connected = true;
  const browser = {
    contexts,
    isConnected: () => connected,
    version: () => "153.0.8010.12",
    on: (event: string, cb: () => void) => (listeners[event] = cb),
    close: vi.fn(async () => {
      connected = false;
    }),
    disconnect: () => {
      connected = false;
      listeners.disconnected?.();
    },
    newContext: async (options: unknown) => {
      const context = { options, close: vi.fn(async () => undefined), pages: () => [] };
      contexts.push(context);
      return context;
    },
  };
  return browser;
}

function setup(install: () => Promise<void> = async () => undefined) {
  const launched: { headless: boolean; browser: ReturnType<typeof fakeBrowser> }[] = [];
  const launcher: BrowserLauncher = {
    launch: async ({ headless }) => {
      const browser = fakeBrowser();
      launched.push({ headless, browser });
      return browser as unknown as Browser;
    },
  };
  const deps: BrowserManagerDeps = { installer: { install }, launcher, logger: silentLogger };
  return { manager: PlaywrightBrowserManager.getInstance(deps), launched };
}

afterEach(() => PlaywrightBrowserManager.resetInstance());

describe("PlaywrightBrowserManager (Singleton)", () => {
  it("siempre devuelve la misma instancia", () => {
    const { manager } = setup();
    expect(PlaywrightBrowserManager.getInstance()).toBe(manager);
  });

  it("exige dependencias la primera vez", () => {
    expect(() => PlaywrightBrowserManager.getInstance()).toThrow("no está inicializado");
  });

  it("pasa de INSTALLING a READY, o a FAILED con el detalle", async () => {
    const ok = setup().manager;
    expect(ok.status()).toEqual({ browser: "INSTALLING" });
    expect(() => ok.assertReady()).toThrow(expect.objectContaining({ code: "BROWSER_NOT_READY" }));
    await ok.initialize();
    expect(ok.status()).toEqual({ browser: "READY" });

    PlaywrightBrowserManager.resetInstance();
    const failed = setup(async () => Promise.reject(new Error("sin red"))).manager;
    await failed.initialize();
    expect(failed.status()).toEqual({ browser: "FAILED", detail: "sin red" });
  });

  it("las sesiones headless comparten un solo Chromium y cierran solo su contexto", async () => {
    const { manager, launched } = setup();
    await manager.initialize();

    const a = await manager.openSession({ visible: false, storageState: { cookies: [] } });
    const b = await manager.openSession({ visible: false });
    expect(launched).toHaveLength(1);
    expect(launched[0].headless).toBe(true);
    expect(launched[0].browser.contexts[0].options).toEqual({
      storageState: { cookies: [] },
      // Sin "HeadlessChrome": algunos portales responden 403 a ese user-agent
      userAgent: desktopUserAgent("153.0.8010.12"),
    });
    expect(desktopUserAgent("153.0.8010.12", "win32")).toBe(
      "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/153.0.8010.12 Safari/537.36",
    );

    await a.close();
    await b.close();
    expect(launched[0].browser.contexts.every((c) => c.close.mock.calls.length === 1)).toBe(true);
    expect(launched[0].browser.close).not.toHaveBeenCalled();
  });

  it("relanza el Chromium compartido si se desconectó", async () => {
    const { manager, launched } = setup();
    await manager.initialize();
    await manager.openSession({ visible: false });
    launched[0].browser.disconnect();
    await manager.openSession({ visible: false });
    expect(launched).toHaveLength(2);
  });

  it("la sesión visible tiene su propio Chromium y lo cierra con ella", async () => {
    const { manager, launched } = setup();
    await manager.initialize();
    const session = await manager.openSession({ visible: true });
    expect(launched[0]).toMatchObject({ headless: false });
    expect(launched[0].browser.contexts[0].options).toEqual({ viewport: null });
    await session.close();
    await session.close();
    expect(launched[0].browser.close).toHaveBeenCalledTimes(1);
  });

  it("al apagar cierra las sesiones abiertas y el Chromium compartido", async () => {
    const { manager, launched } = setup();
    await manager.initialize();
    await manager.openSession({ visible: true });
    await manager.openSession({ visible: false });
    await manager.shutdown();
    expect(launched.every(({ browser }) => !browser.isConnected())).toBe(true);
  });
});

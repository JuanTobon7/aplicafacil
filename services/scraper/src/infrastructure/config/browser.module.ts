import { Logger, Module } from "@nestjs/common";
import { BROWSER_MANAGER } from "../../application/port/out/browser-manager";
import { BROWSER_SESSION_FACTORY } from "../../application/port/out/browser-session-factory";
import { BROWSER_STATUS_PROVIDER } from "../../application/port/out/browser-status-provider";
import { ChromiumLauncher } from "../browser/chromium-launcher";
import { PlaywrightBrowserManager } from "../browser/playwright-browser-manager";
import { PlaywrightChromiumInstaller } from "../browser/playwright-chromium-installer";
import { BrowserLifecycle } from "./browser-lifecycle";

/**
 * Único lugar que conoce la implementación del navegador. El resto pide los puertos: cambiar Playwright por
 * otra cosa (p. ej. la extensión del navegador) es cambiar esta factory.
 */
@Module({
  providers: [
    {
      provide: BROWSER_MANAGER,
      useFactory: () =>
        PlaywrightBrowserManager.getInstance({
          installer: new PlaywrightChromiumInstaller(),
          launcher: new ChromiumLauncher(),
          logger: new Logger("BrowserManager"),
        }),
    },
    { provide: BROWSER_SESSION_FACTORY, useExisting: BROWSER_MANAGER },
    { provide: BROWSER_STATUS_PROVIDER, useExisting: BROWSER_MANAGER },
    BrowserLifecycle,
  ],
  exports: [BROWSER_SESSION_FACTORY, BROWSER_STATUS_PROVIDER],
})
export class BrowserModule {}

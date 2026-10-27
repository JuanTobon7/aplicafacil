import { chromium, type Browser } from "playwright";

/** Lanza procesos del navegador. Separado del gestor para poder probarlo sin Chromium. */
export interface BrowserLauncher {
  launch(options: { headless: boolean }): Promise<Browser>;
}

/** Reduce la señal "navegador automatizado" que algunos sitios usan para bloquear. */
const ARGS = ["--disable-blink-features=AutomationControlled"];

/**
 * User-agent de un Chrome de escritorio con la versión real del Chromium. En headless, Chromium se anuncia como
 * "HeadlessChrome" y hay portales que responden 403 a eso.
 */
export function desktopUserAgent(chromiumVersion: string, platform: NodeJS.Platform = process.platform): string {
  const os =
    platform === "win32" ? "Windows NT 10.0; Win64; x64"
    : platform === "darwin" ? "Macintosh; Intel Mac OS X 10_15_7"
    : "X11; Linux x86_64";
  return `Mozilla/5.0 (${os}) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/${chromiumVersion} Safari/537.36`;
}

export class ChromiumLauncher implements BrowserLauncher {
  launch({ headless }: { headless: boolean }): Promise<Browser> {
    return chromium.launch({ headless, args: ARGS });
  }
}

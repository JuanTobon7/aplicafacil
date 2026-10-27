import { execFile } from "node:child_process";
import { dirname, join } from "node:path";
import { promisify } from "node:util";
import type { BrowserInstaller } from "../../application/port/out/browser-installer";

/** `playwright install chromium`: la primera vez descarga el navegador, luego solo verifica. */
export class PlaywrightChromiumInstaller implements BrowserInstaller {
  async install(): Promise<void> {
    // cli.js no está en "exports" del paquete: se ubica junto a su index.js
    const cli = join(dirname(require.resolve("playwright")), "cli.js");
    await promisify(execFile)(process.execPath, [cli, "install", "chromium"]);
  }
}

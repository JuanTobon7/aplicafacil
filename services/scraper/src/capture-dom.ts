import { existsSync, rmSync } from "node:fs";
import { join } from "node:path";
import type { CaptureOperator, CaptureScript } from "./application/port/out/dom-capture";
import { DomCaptureRunner } from "./application/services/dom-capture-runner";
import { ChromiumLauncher } from "./infrastructure/browser/chromium-launcher";
import { PlaywrightBrowserManager } from "./infrastructure/browser/playwright-browser-manager";
import { PlaywrightChromiumInstaller } from "./infrastructure/browser/playwright-chromium-installer";
import { FileDomSnapshotStore } from "./infrastructure/capture/file-dom-snapshot-store";
import { computrabajoCaptureScript } from "./infrastructure/portal/computrabajo/computrabajo.capture";
import { linkedInCaptureScript } from "./infrastructure/portal/linkedin/linkedin.capture";
import { systemClock } from "./infrastructure/time/system-clock";

/**
 * Captura automática del DOM de un portal para construir y validar sus selectores.
 *
 *   npm run build
 *   node dist/capture-dom.js linkedin [--keywords "desarrollador java"] [--redact "Nombre Apellido,otro texto"]
 *   node dist/capture-dom.js computrabajo
 *
 * Abre una ventana visible; la persona solo inicia sesión ahí (la app nunca ve la contraseña). En cuanto se detecta la
 * sesión, recorre las páginas del guion, las guarda saneadas en services/scraper/dom-snapshots/ (ignorada por git) y
 * cierra la ventana. La sesión de esta captura NO se guarda: para la app se inicia sesión desde "Conectar".
 * Con textos con espacios, llamar a node directamente: en PowerShell `npm run … -- --keywords "a b"` pierde las comillas.
 */
const SCRIPTS: Record<string, (keywords: string) => CaptureScript> = {
  linkedin: linkedInCaptureScript,
  computrabajo: (keywords) => computrabajoCaptureScript(keywords, process.env.COMPUTRABAJO_COUNTRY ?? "co"),
};

const SNAPSHOTS = join(__dirname, "..", "dom-snapshots");
const CONTINUE_FILE = join(SNAPSHOTS, ".continuar");

/** "Ya inicié sesión": crear el archivo dom-snapshots/.continuar (se borra al leerlo). */
const operator: CaptureOperator = {
  say: (message) => console.log(message),
  continueRequested: async () => {
    if (!existsSync(CONTINUE_FILE)) return false;
    rmSync(CONTINUE_FILE);
    return true;
  },
};

function option(args: string[], name: string): string | undefined {
  const i = args.indexOf(name);
  return i >= 0 ? args[i + 1] : undefined;
}

async function main(args: string[]) {
  const script = SCRIPTS[args[0] ?? ""];
  if (!script) {
    console.error(`Uso: node dist/capture-dom.js <${Object.keys(SCRIPTS).join("|")}> [--keywords "..."] [--redact "a,b"]`);
    return 2;
  }
  const browser = PlaywrightBrowserManager.getInstance({
    installer: new PlaywrightChromiumInstaller(),
    launcher: new ChromiumLauncher(),
    logger: { log: operator.say, warn: operator.say, error: operator.say },
  });
  try {
    await browser.initialize();
    if (existsSync(CONTINUE_FILE)) rmSync(CONTINUE_FILE); // una señal vieja no debe saltarse el login
    const runner = new DomCaptureRunner({
      sessions: browser,
      store: new FileDomSnapshotStore(SNAPSHOTS),
      operator,
      clock: systemClock,
    });
    const redact = (option(args, "--redact") ?? "").split(",");
    const saved = await runner.run(script(option(args, "--keywords") ?? "desarrollador"), redact);
    operator.say(`Listo: ${saved} páginas guardadas en services/scraper/dom-snapshots/`);
    return 0;
  } finally {
    await browser.shutdown();
  }
}

main(process.argv.slice(2)).then(
  (code) => process.exit(code),
  (error: Error) => {
    console.error(`La captura falló: ${error.message}`);
    process.exit(1);
  },
);

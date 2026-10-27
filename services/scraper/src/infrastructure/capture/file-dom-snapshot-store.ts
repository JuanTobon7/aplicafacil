import { mkdir, writeFile } from "node:fs/promises";
import { join } from "node:path";
import type { DomSnapshotStore } from "../../application/port/out/dom-capture";
import type { DomSnapshot } from "../../domain/dom-snapshot";

/**
 * Guarda cada página como `<carpeta>/<portal>/<inicio>/<paso>.html` más un `.json` con URL, título y nombres de
 * cookies. La carpeta (`services/scraper/dom-snapshots/`) está en .gitignore: el DOM nunca se versiona.
 */
export class FileDomSnapshotStore implements DomSnapshotStore {
  private readonly runDir: string;

  constructor(
    private readonly root: string,
    startedAt: Date = new Date(),
  ) {
    this.runDir = startedAt.toISOString().replace(/[:.]/g, "-");
  }

  async save(snapshot: DomSnapshot): Promise<string> {
    const dir = join(this.root, snapshot.portal, this.runDir);
    await mkdir(dir, { recursive: true });
    const base = join(dir, snapshot.step);
    const { html, ...meta } = snapshot;
    await writeFile(`${base}.html`, html, "utf8");
    await writeFile(`${base}.json`, `${JSON.stringify(meta, null, 2)}\n`, "utf8");
    return `${base}.html`;
  }
}

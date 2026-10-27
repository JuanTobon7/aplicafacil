import { Inject, Injectable, OnApplicationBootstrap, OnApplicationShutdown } from "@nestjs/common";
import { BROWSER_MANAGER, type BrowserManager } from "../../application/port/out/browser-manager";

/** Conecta el ciclo de vida de Nest con el gestor del navegador, que no depende de Nest. */
@Injectable()
export class BrowserLifecycle implements OnApplicationBootstrap, OnApplicationShutdown {
  constructor(@Inject(BROWSER_MANAGER) private readonly manager: BrowserManager) {}

  onApplicationBootstrap() {
    // Sin await: la API arranca mientras Chromium se instala (/health responde INSTALLING)
    void this.manager.initialize();
  }

  async onApplicationShutdown() {
    await this.manager.shutdown();
  }
}

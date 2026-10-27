import type { StorageState } from "../../../domain/storage-state";
import type { BrowserSession } from "./browser-session";

export type OpenSessionOptions = {
  /** true: ventana visible para que la persona inicie sesión; false: headless. */
  visible: boolean;
  storageState?: StorageState;
  /**
   * Graba un video del contexto en esa carpeta (lo guarda al cerrar la sesión). Solo para búsquedas: el inicio de
   * sesión nunca se graba, así la contraseña no queda en un video.
   */
  recordVideoDir?: string;
};

export interface BrowserSessionFactory {
  /** Lanza ScraperError(BROWSER_NOT_READY) si el navegador aún no se puede usar. */
  assertReady(): void;
  openSession(options: OpenSessionOptions): Promise<BrowserSession>;
}

export const BROWSER_SESSION_FACTORY = Symbol("BrowserSessionFactory");

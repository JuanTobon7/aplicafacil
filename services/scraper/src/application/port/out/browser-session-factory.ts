import type { StorageState } from "../../../domain/storage-state";
import type { BrowserSession } from "./browser-session";

export type OpenSessionOptions = {
  /** true: ventana visible para que la persona inicie sesión; false: headless. */
  visible: boolean;
  storageState?: StorageState;
};

export interface BrowserSessionFactory {
  /** Lanza ScraperError(BROWSER_NOT_READY) si el navegador aún no se puede usar. */
  assertReady(): void;
  openSession(options: OpenSessionOptions): Promise<BrowserSession>;
}

export const BROWSER_SESSION_FACTORY = Symbol("BrowserSessionFactory");

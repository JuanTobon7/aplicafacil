import type { BrowserSessionFactory } from "./browser-session-factory";
import type { BrowserStatusProvider } from "./browser-status-provider";

/** Dueño único del motor del navegador en el proceso: instalación, sesiones y cierre ordenado. */
export interface BrowserManager extends BrowserSessionFactory, BrowserStatusProvider {
  initialize(): Promise<void>;
  shutdown(): Promise<void>;
}

export const BROWSER_MANAGER = Symbol("BrowserManager");

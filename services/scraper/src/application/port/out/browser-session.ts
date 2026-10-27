import type { StorageState } from "../../../domain/storage-state";
import type { BrowserPage } from "./browser-page";

export type SessionCookie = { name: string; value: string };

export type SessionHttpResponse = { ok: boolean; status: number; json(): Promise<unknown> };

export interface SessionPages {
  newPage(): Promise<BrowserPage>;
  /** URL de la pestaña más reciente, o null si no queda ninguna. */
  currentUrl(): string | null;
}

export interface SessionCookies {
  cookies(origin: string): Promise<SessionCookie[]>;
}

/** Peticiones HTTP con las cookies de la sesión (APIs internas del portal). */
export interface SessionHttpClient {
  get(url: string, headers?: Record<string, string>): Promise<SessionHttpResponse>;
}

export interface SessionLifecycle {
  /** false cuando la persona cerró la ventana o el navegador se desconectó. */
  isOpen(): boolean;
  exportState(): Promise<StorageState>;
  /** Idempotente. */
  close(): Promise<void>;
}

/** Contexto aislado y NO persistente del navegador. Los portales solo trabajan contra esta abstracción. */
export interface BrowserSession extends SessionPages, SessionCookies, SessionHttpClient, SessionLifecycle {}

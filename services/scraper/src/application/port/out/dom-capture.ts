import type { DomSnapshot } from "../../../domain/dom-snapshot";

/** Dónde quedan las páginas capturadas (una carpeta local ignorada por git). */
export interface DomSnapshotStore {
  /** @return la ruta donde quedó, para mostrarla */
  save(snapshot: DomSnapshot): Promise<string>;
}

/** Avisa en la terminal qué está pasando. La captura no pide nada: la persona solo inicia sesión en la ventana. */
export interface CaptureOperator {
  say(message: string): void;
  /**
   * Señal manual de "ya inicié sesión", para un portal cuyo login todavía no se sabe detectar (por ejemplo, un archivo
   * que se crea). Se consume al leerla.
   */
  continueRequested(): Promise<boolean>;
}

/** Lo que se sabe del navegador mientras la persona inicia sesión (nunca valores de cookies). */
export type LoginProbe = {
  url: string;
  cookieNames: string[];
  /** Cookies que ya había en la página de inicio de sesión, antes de iniciarla. */
  initialCookieNames: string[];
};

/**
 * Un paso del recorrido: abrir una URL (fija o sacada de la página anterior, por ejemplo un enlace del menú) y guardar
 * la página. Sin URL se guarda la página en la que quedó el navegador.
 */
export type CaptureStep = {
  name: string;
  /** Qué se ve en este paso, para la terminal. */
  description: string;
  url?: string | ((previousHtml: string) => string | null);
  /** Selector a esperar antes de guardar (la página carga sus datos después del HTML inicial). */
  waitFor?: string;
};

/** El recorrido de un portal: dónde se inicia sesión, cómo saber que ya se inició y qué páginas guardar. */
export type CaptureScript = {
  portal: string;
  displayName: string;
  loginUrl: string;
  /** Origen cuyas cookies se listan (solo nombres). */
  origin: string;
  /** Otros orígenes del portal con cookies propias (el acceso de candidatos en otro subdominio, por ejemplo). */
  extraCookieOrigins?: string[];
  /** true cuando la persona terminó de iniciar sesión (2FA incluido): ahí la captura sigue sola. */
  loggedIn: (probe: LoginProbe) => boolean;
  steps: CaptureStep[];
};

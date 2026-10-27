export type NavigationOptions = { waitUntil?: "load" | "domcontentloaded"; timeoutMs?: number };

/** Una pestaña, sin exponer el motor (Playwright, extensión del navegador, …). */
export interface BrowserPage {
  goto(url: string, options?: NavigationOptions): Promise<void>;
  url(): string;
  html(): Promise<string>;
  /** Texto del primer elemento que coincide con el selector, o null si no hay. */
  textOf(selector: string): Promise<string | null>;
  /** Espera a que aparezca el selector. false si no aparece en `timeoutMs` (no lanza). */
  waitFor(selector: string, timeoutMs: number): Promise<boolean>;
  /**
   * Desplaza hasta el final (de `container` o de la página) para que carguen los elementos perezosos.
   * Se detiene al llegar al final o tras `maxSteps` pasos.
   */
  scrollToEnd(container?: string, maxSteps?: number): Promise<void>;
  /**
   * Lee cada elemento que coincide con `selector` y devuelve un registro por elemento con los campos pedidos.
   * Es una descripción en datos (no código que recibe el DOM), así funciona con cualquier motor.
   */
  extractAll(selector: string, fields: Record<string, FieldSpec>): Promise<ExtractedRecord[]>;
  close(): Promise<void>;
}

/**
 * Cómo leer un campo dentro de un elemento: el texto o un atributo del primer descendiente que coincide con
 * `selector` (o del propio elemento si se omite).
 */
export type FieldSpec = { selector?: string; attribute?: string };

export type ExtractedRecord = Record<string, string | null>;

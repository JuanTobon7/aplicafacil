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
  /**
   * Para grabaciones: desplaza hasta el elemento `index` de `selector`, lo resalta, hace clic en `open` dentro de él
   * (si se indica) y lo deja a la vista `holdMs`. Si la página navega con el clic, vuelve al listado. Nunca lanza:
   * una card que ya no está solo se salta.
   */
  spotlight(selector: string, index: number, options: SpotlightOptions): Promise<void>;
  close(): Promise<void>;
}

/**
 * Cómo leer un campo dentro de un elemento: el texto o un atributo del primer descendiente que coincide con
 * `selector` (o del propio elemento si se omite).
 */
export type FieldSpec = { selector?: string; attribute?: string };

export type ExtractedRecord = Record<string, string | null>;

/** `close`: qué clic vuelve al listado si el detalle lo tapó (ventana angosta). */
export type SpotlightOptions = { open?: string; close?: string; holdMs: number };

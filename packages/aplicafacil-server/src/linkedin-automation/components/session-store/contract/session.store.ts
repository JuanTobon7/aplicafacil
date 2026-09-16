import { Page } from 'puppeteer';

/**
 * Almacén de sesión de LinkedIn.
 *
 * Persiste las cookies de la sesión autenticada en un archivo JSON para
 * no tener que iniciar sesión en cada ejecución del navegador.
 */
export abstract class SessionStore {
  /**
   * Guarda las cookies de LinkedIn de la página en el archivo de sesión.
   */
  abstract save(page: Page): Promise<void>;

  /**
   * Carga las cookies guardadas en la página.
   *
   * @returns `true` si había una sesión guardada y se restauró, `false` si no.
   */
  abstract load(page: Page): Promise<boolean>;

  /**
   * Elimina el archivo de sesión guardado.
   */
  abstract clear(): Promise<void>;
}
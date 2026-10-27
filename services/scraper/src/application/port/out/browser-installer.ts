/** Deja el binario del navegador listo para lanzarse (p. ej. descarga Chromium en el primer arranque). */
export interface BrowserInstaller {
  install(): Promise<void>;
}

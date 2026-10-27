/** Lo mínimo que la aplicación necesita para loguear; el Logger de Nest lo cumple. Nunca recibe secretos. */
export interface AppLogger {
  log(message: string): void;
  warn(message: string): void;
  error(message: string): void;
}
